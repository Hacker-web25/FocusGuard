package com.dhruv.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.dhruv.focusguard.FocusGuardApp
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.data.repository.TaskRepository
import com.dhruv.focusguard.guard.GuardEngine
import com.dhruv.focusguard.guard.LockOverlay
import com.dhruv.focusguard.guard.PrefsGuardStore
import com.dhruv.focusguard.guard.TamperRules
import com.dhruv.focusguard.guard.Watchdog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guards Instagram.
 *
 * On every window change it works out which *app* is on top (ignoring the
 * keyboard, system UI and our own overlay), feeds that to [GuardEngine], and
 * shows or hides [LockOverlay] accordingly. It also blocks the settings screens
 * that would switch FocusGuard off (see [TamperRules]).
 */
class AppDetectorService : AccessibilityService() {

    private lateinit var repo: TaskRepository
    private lateinit var store: PrefsGuardStore
    private lateinit var engine: GuardEngine
    private lateinit var overlay: LockOverlay

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var lastTamperCheckAt = 0L
    private var lastTamperBlockAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = (application as FocusGuardApp).repository
        store = PrefsGuardStore(this)
        engine = GuardEngine(store)
        engine.recoverAfterRestart(System.currentTimeMillis())
        overlay = LockOverlay(
            context = this,
            onCountdownFinished = { engine.onCountdownFinished() },
            onContinue = { applyDecision(engine.onContinue()) },
            onCloseTarget = {
                applyDecision(engine.onCloseTarget())
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        )
        running = true
        Watchdog.schedule(this)
        Log.i(TAG, "Service connected")

        // If Instagram is already open (e.g. we were just restarted), lock it now.
        handler.postDelayed({ evaluateForeground() }, 300)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::engine.isInitialized) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                evaluateForeground()
                maybeCheckTamper(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> maybeCheckTamper(event)
        }
    }

    // ── Foreground detection ────────────────────────────────────

    private fun evaluateForeground() {
        val pkg = topAppPackage() ?: return
        val isTarget = pkg in repo.monitoredApps
        val decision = engine.onForeground(
            isTarget = isTarget,
            baseSeconds = repo.timerDurationSec,
            nowElapsed = SystemClock.elapsedRealtime(),
            nowWall = System.currentTimeMillis()
        )
        applyDecision(decision)
    }

    /**
     * Package of the top-most *application* window. Overlays, the keyboard,
     * status bar and notification shade are different window types and are
     * skipped, so pulling down the shade or typing never counts as leaving.
     */
    private fun topAppPackage(): String? {
        val list = try {
            windows
        } catch (e: Exception) {
            Log.w(TAG, "getWindows failed", e)
            return null
        }
        var best: AccessibilityWindowInfo? = null
        for (w in list) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (best == null || w.layer > best.layer) best = w
        }
        return best?.root?.packageName?.toString()
    }

    private fun applyDecision(decision: GuardEngine.Decision) {
        when (decision) {
            is GuardEngine.Decision.Show -> {
                overlay.show(
                    totalSeconds = decision.totalSeconds,
                    penaltySeconds = decision.penaltySeconds,
                    strikes = decision.strikes,
                    justPenalized = decision.justPenalized,
                    userName = repo.userName
                )
                loadOverlayContent()
                Log.i(TAG, "Locked: ${decision.totalSeconds}s (strikes=${decision.strikes})")
            }
            GuardEngine.Decision.Hide -> overlay.hide()
            GuardEngine.Decision.None -> Unit
        }
    }

    private fun loadOverlayContent() {
        scope.launch {
            val (tasks, goal) = withContext(Dispatchers.IO) {
                val t = repo.getActiveTasksOnce()
                    .sortedWith(
                        compareByDescending<Task> { it.priority.weight }
                            .thenBy { it.deadline ?: Long.MAX_VALUE }
                    )
                val g = repo.getAllGoalsOnce()
                    .filter { it.progress < 100 }
                    .minByOrNull { it.progress }
                t to g
            }
            overlay.setContent(tasks, goal)
        }
    }

    // ── Tamper lock ─────────────────────────────────────────────

    private fun maybeCheckTamper(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in TamperRules.WATCHED_PACKAGES) return
        if (!repo.onboardingDone) return            // never block setup
        if (store.isTamperUnlocked()) return         // maintenance window

        val now = SystemClock.elapsedRealtime()
        if (now - lastTamperCheckAt < 250) return
        lastTamperCheckAt = now
        if (now - lastTamperBlockAt < 1500) return

        val list = try { windows } catch (e: Exception) { return }
        for (w in list) {
            val root = w.root ?: continue
            val wPkg = root.packageName?.toString() ?: continue
            if (wPkg !in TamperRules.WATCHED_PACKAGES) continue
            if (TamperRules.isTamperScreen(wPkg, collectTexts(root))) {
                blockTamper()
                return
            }
        }
    }

    private fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited++
            node.text?.let { out.add(it.toString().lowercase()) }
            node.contentDescription?.let { out.add(it.toString().lowercase()) }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it) }
            }
        }
        return out
    }

    private fun blockTamper() {
        lastTamperBlockAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "Blocked a screen that could disable FocusGuard")
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 150)
        Toast.makeText(
            this,
            "FocusGuard is locked. Open FocusGuard → Settings → Unlock for 5 minutes.",
            Toast.LENGTH_LONG
        ).show()
    }

    // ── Lifecycle ───────────────────────────────────────────────

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        running = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running = false
        if (::overlay.isInitialized) overlay.hide()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AppDetector"

        /** In-process liveness flag, for UI that runs in the same process. */
        @Volatile
        var running: Boolean = false
            private set
    }
}
