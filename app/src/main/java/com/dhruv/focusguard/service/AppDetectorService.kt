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
 * One source of truth decides what's on top: the window list, i.e. the
 * top-most *application* window (ignoring the keyboard, system UI and our
 * overlay). Instagram's own window event is only used to (a) mute its audio
 * instantly and (b) re-read the window list a few times while it catches up.
 * It never opens or closes the lock by itself; doing that made the pause
 * flicker open/shut while Instagram was cold-starting.
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

    private var lastShowAt = 0L
    private var pendingLeaveSince = 0L
    private var contentEventsOn = false
    private var lastTamperCheckAt = 0L
    private var lastTamperBlockAt = 0L

    private val recheck = Runnable { evaluateForeground() }
    private val recheckLate = Runnable { evaluateForeground() }
    private val audioRelease = Runnable { if (!overlay.isShowing) audio.restore() }
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
                if (pkg != null && pkg in repo.monitoredApps) onTargetWindowEvent()
                evaluateForeground()
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

    /**
     * Instagram announced a window. Silence its audio right away (the window
     * list may lag a few frames) and re-read the list shortly after so the lock
     * lands as soon as Android reports Instagram on top. If the lock never
     * comes (e.g. an unlocked session), audio is handed back after 2s.
     */
    private fun onTargetWindowEvent() {
        if (!overlay.isShowing && !engine.unlocked) {
            audio.silence()
            handler.removeCallbacks(audioRelease)
            handler.postDelayed(audioRelease, 2000)
        }
        handler.removeCallbacks(recheck)
        handler.removeCallbacks(recheckLate)
        handler.postDelayed(recheck, 90)
        handler.postDelayed(recheckLate, 300)
    }

    private fun evaluateForeground() {
        val pkg = topAppPackage() ?: return
        setContentEvents(pkg in TamperRules.WATCHED_PACKAGES)

        val now = SystemClock.elapsedRealtime()
        val isTarget = pkg in repo.monitoredApps

        if (isTarget) {
            pendingLeaveSince = 0L
        } else if (overlay.isShowing && now - lastShowAt < SETTLE_MS) {
            // Just locked: while Instagram is still starting, the list can blip
            // back to the previous app for a moment. Only believe "left" once it
            // has held for CONFIRM_LEAVE_MS; a real Home/Recents easily does.
            if (pendingLeaveSince == 0L) pendingLeaveSince = now
            if (now - pendingLeaveSince < CONFIRM_LEAVE_MS) {
                handler.removeCallbacks(recheck)
                handler.postDelayed(recheck, CONFIRM_LEAVE_MS + 20)
                Log.d(TAG, "Holding lock: list says $pkg ${now - lastShowAt}ms after locking")
                return
            }
        }

        val decision = engine.onForeground(
            isTarget = isTarget,
            baseSeconds = repo.timerDurationSec,
            nowElapsed = now,
            nowWall = System.currentTimeMillis()
        )
        if (decision != GuardEngine.Decision.None) {
            Log.i(TAG, "top=$pkg target=$isTarget -> ${decision.javaClass.simpleName}")
        }
        if (!isTarget) pendingLeaveSince = 0L
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
                // Silence first: this is the part the user hears.
                audio.silence()
                handler.removeCallbacks(audioRelease)
                lastShowAt = SystemClock.elapsedRealtime()
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

        /** Window after locking during which a "left Instagram" reading must persist. */
        private const val SETTLE_MS = 2_500L
        /** How long that reading must hold before the pause closes. */
        private const val CONFIRM_LEAVE_MS = 350L

        private const val BASE_EVENTS =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED

        /** In-process liveness flag, for UI that runs in the same process. */
        @Volatile
        var running: Boolean = false
            private set
    }
}
