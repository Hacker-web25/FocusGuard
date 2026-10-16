package com.dhruv.focusguard.ui.focus

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FocusMode { Timer, Stopwatch }

enum class FocusPhase { Idle, Running, Paused, Finished }

/**
 * One focus session. Pure data, times on the elapsed-realtime clock, so a
 * session keeps counting while the app is in the background or killed, and
 * the wall clock changing can't skew it.
 *
 * For the stopwatch, [targetMs] is not a limit, just the goal the audience
 * measures your progress against.
 */
data class FocusSession(
    val mode: FocusMode,
    val taskId: Long?,
    val targetMs: Long,
    val bankedMs: Long = 0L,
    val runningSince: Long = -1L,
    val finished: Boolean = false
) {
    val running: Boolean get() = runningSince >= 0

    fun elapsed(now: Long): Long = bankedMs + if (running) (now - runningSince).coerceAtLeast(0) else 0

    fun remaining(now: Long): Long = (targetMs - elapsed(now)).coerceAtLeast(0)

    fun progress(now: Long): Float =
        if (targetMs <= 0) 0f else (elapsed(now).toFloat() / targetMs).coerceIn(0f, 1f)

    fun phase(now: Long): FocusPhase = when {
        finished || (mode == FocusMode.Timer && elapsed(now) >= targetMs) -> FocusPhase.Finished
        running -> FocusPhase.Running
        bankedMs > 0 -> FocusPhase.Paused
        else -> FocusPhase.Idle
    }

    fun start(now: Long) = if (running || finished) this else copy(runningSince = now)
    fun pause(now: Long) = if (!running) this else copy(bankedMs = elapsed(now), runningSince = -1)
    fun reset() = copy(bankedMs = 0, runningSince = -1, finished = false)
    fun finish(now: Long) = copy(bankedMs = if (mode == FocusMode.Timer) targetMs else elapsed(now), runningSince = -1, finished = true)

    /** Only while not started: the timer's length (whole minutes, 1 min .. 4 h). */
    fun withTarget(ms: Long) = copy(targetMs = ms.coerceIn(MIN_TARGET, MAX_TARGET))

    companion object {
        const val MIN_TARGET = 60_000L
        const val MAX_TARGET = 4 * 60 * 60_000L
        const val DEFAULT_MINUTES = 25
    }
}

/**
 * The app-wide focus session: survives leaving the screen and the process
 * being killed (kept in prefs), so coming back shows the right time.
 */
object FocusClock {
    private const val PREFS = "focus_clock"
    private val _session = MutableStateFlow<FocusSession?>(null)
    val session: StateFlow<FocusSession?> = _session.asStateFlow()
    private var loaded = false

    fun now(): Long = SystemClock.elapsedRealtime()

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("mode")) return
        _session.value = FocusSession(
            mode = FocusMode.entries.getOrElse(p.getInt("mode", 0)) { FocusMode.Timer },
            taskId = p.getLong("task", -1L).takeIf { it > 0 },
            targetMs = p.getLong("target", FocusSession.DEFAULT_MINUTES * 60_000L),
            bankedMs = p.getLong("banked", 0L),
            runningSince = p.getLong("since", -1L),
            finished = p.getBoolean("finished", false)
        ).let { s ->
            // A reboot resets elapsed-realtime; a session can't have started in the future.
            if (s.runningSince > now()) s.copy(runningSince = -1) else s
        }
    }

    /**
     * Opens the focus screen's session. A session that is counting keeps
     * going (you have to reset it to switch); otherwise a fresh one is set up
     * for this mode and task.
     */
    fun prepare(context: Context, mode: FocusMode, taskId: Long?, minutes: Int?) {
        load(context)
        val cur = _session.value
        val now = now()
        if (cur != null && (cur.running || cur.phase(now) == FocusPhase.Paused) && (cur.mode != mode || cur.taskId != taskId)) return
        if (cur != null && cur.mode == mode && cur.taskId == taskId && cur.phase(now) != FocusPhase.Idle) return
        val target = ((minutes?.takeIf { it > 0 } ?: cur?.takeIf { it.mode == mode && it.taskId == taskId }?.let { (it.targetMs / 60_000L).toInt() }
            ?: FocusSession.DEFAULT_MINUTES).toLong()) * 60_000L
        set(context, FocusSession(mode, taskId, target))
    }

    fun update(context: Context, change: (FocusSession) -> FocusSession) {
        val cur = _session.value ?: return
        set(context, change(cur))
    }

    private fun set(context: Context, s: FocusSession) {
        _session.value = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("mode", s.mode.ordinal)
            .putLong("task", s.taskId ?: -1L)
            .putLong("target", s.targetMs)
            .putLong("banked", s.bankedMs)
            .putLong("since", s.runningSince)
            .putBoolean("finished", s.finished)
            .apply()
    }

    /** For previews and screenshot tests. */
    fun setForPreview(s: FocusSession?) {
        loaded = true
        _session.value = s
    }
}

/** "1:30:00" from an hour up (or when [forceHours]), otherwise "25:00". Whole seconds, rounded down. */
fun clockText(ms: Long, forceHours: Boolean = false): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0 || forceHours) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
