package com.dhruv.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.dhruv.focusguard.FocusGuardApp
import com.dhruv.focusguard.MainActivity
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.data.repository.TaskRepository
import com.dhruv.focusguard.guard.AudioGuard
import com.dhruv.focusguard.guard.GuardEngine
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.guard.LockOverlay
import com.dhruv.focusguard.guard.PrefsGuardStore
import com.dhruv.focusguard.guard.TamperRules
import com.dhruv.focusguard.guard.Watchdog
import com.dhruv.focusguard.guard.overlay.PauseActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guards Instagram.
 *
 * Two signals decide what's on top:
 *  - Fast path: Instagram's own window-state event locks immediately, before
 *    the window list has caught up, so reels get as little screen and audio
 *    time as possible.
 *  - Window list: the top-most *application* window (ignoring the keyboard,
 *    system UI and our overlay) confirms or corrects it, and detects leaving.
 *
 * It also blocks the settings screens that would switch FocusGuard off
 * ([TamperRules]); content events for that are only subscribed while one of
 * those apps is on top, so Instagram's feed doesn't flood the service.
 */
class AppDetectorService : AccessibilityService() {

    private lateinit var repo: TaskRepository
    private lateinit var store: PrefsGuardStore
    private lateinit var engine: GuardEngine
    private lateinit var overlay: LockOverlay
    private lateinit var audio: AudioGuard
    private lateinit var haptics: Haptics

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var fastTargetAt = 0L
    private var contentEventsOn = false
    private var lastTamperCheckAt = 0L
    private var lastTamperBlockAt = 0L

    private val recheck = Runnable { evaluateForeground() }
    private val tamperRecheck = Runnable { maybeCheckTamper(null) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = (application as FocusGuardApp).repository
        store = PrefsGuardStore(this)
        engine = GuardEngine(store)
        audio = AudioGuard(this, store)
        haptics = Haptics(this)
        engine.recoverAfterRestart(System.currentTimeMillis())
        audio.restore() // undo a mute left behind if we were killed mid-pause
        overlay = LockOverlay(this, haptics, pauseActions)
        setContentEvents(false)
        running = true
        Watchdog.schedule(this)
        Log.i(TAG, "Service connected")

        // If Instagram is already open (e.g. we were just restarted), lock it now.
        handler.postDelayed({ evaluateForeground() }, 300)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::engine.isInitialized) return
        val pkg = event.packageName?.toString()

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg != null && pkg in repo.monitoredApps) {
                    onTargetWindowEvent()
                } else {
                    evaluateForeground()
                }
                scheduleTamperChecks(pkg)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                evaluateForeground()
                scheduleTamperChecks(null)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> maybeCheckTamper(pkg)
        }
    }

    // ── Foreground detection ────────────────────────────────────

    /** Instagram reported a new window: lock now, confirm with the window list shortly. */
    private fun onTargetWindowEvent() {
        fastTargetAt = SystemClock.elapsedRealtime()
        applyDecision(
            engine.onForeground(
                isTarget = true,
                baseSeconds = repo.timerDurationSec,
                nowElapsed = fastTargetAt,
                nowWall = System.currentTimeMillis()
            )
        )
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, FAST_GUARD_MS + 60)
    }

    private fun evaluateForeground() {
        val pkg = topAppPackage() ?: return
        setContentEvents(pkg in TamperRules.WATCHED_PACKAGES)

        val isTarget = pkg in repo.monitoredApps
        // Right after Instagram's own event the window list can still show the
        // previous app. Don't let that stale view unlock it; the delayed
        // recheck will settle it.
        if (!isTarget && SystemClock.elapsedRealtime() - fastTargetAt < FAST_GUARD_MS) return

        applyDecision(
            engine.onForeground(
                isTarget = isTarget,
                baseSeconds = repo.timerDurationSec,
                nowElapsed = SystemClock.elapsedRealtime(),
                nowWall = System.currentTimeMillis()
            )
        )
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
                // Silence first: this is the part the user hears.
                audio.silence()
                store.recordOpen()
                overlay.show(
                    totalSeconds = decision.totalSeconds,
                    penaltySeconds = decision.penaltySeconds,
                    strikes = decision.strikes,
                    justPenalized = decision.justPenalized,
                    userName = repo.userName,
                    opensToday = store.opensToday,
                    walkAwaysToday = store.walkAwaysToday
                )
                loadOverlayContent()
                Log.i(TAG, "Locked: ${decision.totalSeconds}s (strikes=${decision.strikes})")
            }
            GuardEngine.Decision.Hide -> {
                overlay.hide()
                audio.restore()
            }
            GuardEngine.Decision.None -> Unit
        }
    }

    private fun loadOverlayContent() {
        scope.launch {
            val (tasks, goal) = withContext(Dispatchers.IO) {
                val t = repo.getActiveTasksOnce()
                val g = repo.getAllGoalsOnce()
                    .filter { it.progress < 100 }
                    .minByOrNull { it.progress }
                t to g
            }
            overlay.setContent(tasks, goal)
        }
    }

    // ── What the pause screen can ask for ───────────────────────

    private val pauseActions = object : PauseActions {
        override fun countdownFinished() = engine.onCountdownFinished()

        override fun continueToInstagram() = applyDecision(engine.onContinue())

        override fun diverted() = engine.onDiverted()

        override fun walkAway(): Int {
            engine.onDiverted()
            store.recordWalkAway()
            return store.walkAwaysToday
        }

        override fun celebrationDone() {
            applyDecision(engine.onCloseTarget())
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        override fun completeTask(task: Task) {
            scope.launch(Dispatchers.IO) { repo.markComplete(task.id) }
        }

        override fun recordFocus(minutes: Int) = store.recordFocusMinutes(minutes)

        override fun addTask() {
            engine.onDiverted()
            try {
                startActivity(
                    Intent(this@AppDetectorService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN_ADD_TASK, true)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not open FocusGuard", e)
            }
        }
    }

    // ── Tamper lock ─────────────────────────────────────────────

    /**
     * Subscribe to content-changed events only while a settings/launcher app is
     * on top. Elsewhere (Instagram especially) they fire constantly and would
     * just add latency; the zero notification timeout keeps the lock instant.
     */
    private fun setContentEvents(on: Boolean) {
        if (on == contentEventsOn && serviceInfo != null) return
        val info = serviceInfo ?: return
        info.eventTypes = if (on) BASE_EVENTS or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED else BASE_EVENTS
        info.notificationTimeout = if (on) 100L else 0L
        serviceInfo = info
        contentEventsOn = on
    }

    /** Settings pages often fill in after the window event; look again shortly after. */
    private fun scheduleTamperChecks(pkgHint: String?) {
        if (pkgHint != null && pkgHint !in TamperRules.WATCHED_PACKAGES) return
        maybeCheckTamper(pkgHint)
        handler.removeCallbacks(tamperRecheck)
        handler.postDelayed(tamperRecheck, 450)
        handler.postDelayed(tamperRecheck, 1200)
    }

    private fun maybeCheckTamper(pkgHint: String?) {
        if (pkgHint != null && pkgHint !in TamperRules.WATCHED_PACKAGES) return
        if (!repo.onboardingDone) return            // never block setup
        if (store.isTamperUnlocked()) return         // maintenance window

        val now = SystemClock.elapsedRealtime()
        if (now - lastTamperCheckAt < 200) return
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
        haptics.thud()
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

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running = false
        if (::overlay.isInitialized) overlay.hide()
        if (::audio.isInitialized) audio.restore()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AppDetector"

        /** How long a fast-path lock ignores a stale "something else is on top". */
        private const val FAST_GUARD_MS = 700L

        private const val BASE_EVENTS =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED

        /** In-process liveness flag, for UI that runs in the same process. */
        @Volatile
        var running: Boolean = false
            private set
    }
}
