package com.dhruv.focusguard.guard

/**
 * Persistent state the engine needs to survive the service being killed.
 * Implemented over SharedPreferences by [PrefsGuardStore]; kept as an
 * interface so the rules below stay free of Android types.
 *
 * All timestamps here are wall-clock millis (System.currentTimeMillis()).
 */
interface GuardStore {
    var strikes: Int
    var lastStrikeAt: Long
    /** When the user last walked away from an unfinished pause (0 = never / cleared). */
    var abandonedAt: Long
    /** When the currently running pause started (0 = no pause running). */
    var timerStartedAt: Long
}

/**
 * The rules for when Instagram is locked.
 *
 *  - Instagram comes to the front and there is no unlocked session -> show the pause.
 *  - Finishing the countdown unlocks Instagram until the user leaves it.
 *  - Coming back within [GRACE_MS] of leaving keeps the session; later means a new pause.
 *  - Leaving during a pause and coming back within [ABANDON_WINDOW_MS] is a skip
 *    attempt: +[STRIKE_SECONDS] per strike, capped at [MAX_PENALTY_SECONDS].
 *    Strikes stack only while the user keeps abandoning; sitting through one
 *    full pause pays the penalty off and resets it to zero.
 *  - Choosing a diversion on the pause screen (breathe, focus, add a task) is
 *    the behaviour we want, so leaving after that is never a skip attempt.
 *  - Strikes also clear after [STRIKE_RESET_MS] without a new one.
 *
 * Time-sensitive comparisons for the session grace use elapsed-realtime (immune to
 * clock changes); persisted values use wall-clock so they survive reboots.
 */
class GuardEngine(private val store: GuardStore) {

    sealed interface Decision {
        data object None : Decision
        data object Hide : Decision
        data class Show(
            val totalSeconds: Int,
            val strikes: Int,
            val penaltySeconds: Int,
            val justPenalized: Boolean
        ) : Decision
    }

    /** Is the monitored app currently the top app? */
    var inTarget = false
        private set

    /** Is the pause overlay currently on screen? */
    var lockShowing = false
        private set

    /** Has the countdown finished for the current visit? */
    var unlocked = false
        private set

    /** Elapsed-realtime when the user left an unlocked session (0 = not left). */
    private var leftUnlockedAt = 0L

    /** User picked a diversion during this pause; leaving now is not penalised. */
    private var diverted = false

    /**
     * Call once when the service (re)connects. If a pause was running when the
     * process died, the user most likely killed us to get past it: treat that as
     * walking away, so the next open is penalised.
     */
    fun recoverAfterRestart(nowWall: Long) {
        val started = store.timerStartedAt
        if (started > 0) {
            store.abandonedAt = started
            store.timerStartedAt = 0
        }
        inTarget = false
        lockShowing = false
        unlocked = false
        diverted = false
        leftUnlockedAt = 0
    }

    /**
     * The top app changed (or was re-checked).
     *
     * @param isTarget whether the new top app is a monitored app.
     * @param baseSeconds the user's configured pause length.
     */
    fun onForeground(isTarget: Boolean, baseSeconds: Int, nowElapsed: Long, nowWall: Long): Decision {
        if (isTarget) {
            if (inTarget) {
                // Still inside Instagram. Self-heal if somehow neither locked nor unlocked.
                return if (!lockShowing && !unlocked) startPause(baseSeconds, nowWall) else Decision.None
            }
            inTarget = true

            if (unlocked && leftUnlockedAt > 0 && nowElapsed - leftUnlockedAt <= GRACE_MS) {
                // Quick switch away and back: keep the session.
                leftUnlockedAt = 0
                return Decision.None
            }
            unlocked = false
            leftUnlockedAt = 0
            return startPause(baseSeconds, nowWall)
        }

        // Some other app is on top.
        if (!inTarget) return Decision.None
        inTarget = false

        if (lockShowing) {
            lockShowing = false
            when {
                // Countdown was already done, they just never tapped Continue.
                unlocked -> leftUnlockedAt = nowElapsed
                // They chose to breathe / focus / add a task. That's a win.
                diverted -> {
                    store.timerStartedAt = 0
                    store.abandonedAt = 0
                }
                else -> {
                    store.timerStartedAt = 0
                    store.abandonedAt = nowWall
                }
            }
            diverted = false
            return Decision.Hide
        }

        if (unlocked) leftUnlockedAt = nowElapsed
        return Decision.None
    }

    /**
     * The countdown reached zero. Instagram is now allowed for this visit, and
     * any penalty has been served in full, so it is wiped.
     */
    fun onCountdownFinished() {
        unlocked = true
        store.timerStartedAt = 0
        store.abandonedAt = 0
        store.strikes = 0
        store.lastStrikeAt = 0
    }

    /**
     * The user picked a diversion (breathe, focus sprint, add a task). The lock
     * stays up, but walking away from here is no longer a skip attempt.
     */
    fun onDiverted() {
        diverted = true
        store.timerStartedAt = 0
        store.abandonedAt = 0
    }

    /** User tapped Continue after the countdown. */
    fun onContinue(): Decision {
        if (!unlocked) return Decision.None
        lockShowing = false
        return Decision.Hide
    }

    /**
     * User tapped "Close Instagram". This is the behaviour we want, so it is never
     * penalised. The caller sends the user home.
     */
    fun onCloseTarget(): Decision {
        lockShowing = false
        unlocked = false
        diverted = false
        leftUnlockedAt = 0
        store.timerStartedAt = 0
        store.abandonedAt = 0
        return Decision.Hide
    }

    /** Current penalty, for display in settings. */
    fun currentPenaltySeconds(nowWall: Long): Int {
        decayStrikes(nowWall)
        return penaltyFor(store.strikes)
    }

    private fun startPause(baseSeconds: Int, nowWall: Long): Decision {
        decayStrikes(nowWall)

        var penalized = false
        val abandoned = store.abandonedAt
        if (abandoned > 0 && nowWall - abandoned in 0..ABANDON_WINDOW_MS) {
            store.strikes = store.strikes + 1
            store.lastStrikeAt = nowWall
            penalized = true
        }
        store.abandonedAt = 0

        val penalty = penaltyFor(store.strikes)
        store.timerStartedAt = nowWall
        lockShowing = true
        unlocked = false
        diverted = false
        return Decision.Show(
            totalSeconds = baseSeconds.coerceAtLeast(1) + penalty,
            strikes = store.strikes,
            penaltySeconds = penalty,
            justPenalized = penalized
        )
    }

    private fun decayStrikes(nowWall: Long) {
        if (store.strikes > 0 && nowWall - store.lastStrikeAt > STRIKE_RESET_MS) {
            store.strikes = 0
        }
    }

    private fun penaltyFor(strikes: Int): Int =
        (strikes * STRIKE_SECONDS).coerceAtMost(MAX_PENALTY_SECONDS)

    companion object {
        const val GRACE_MS = 10_000L
        const val ABANDON_WINDOW_MS = 10 * 60_000L
        const val STRIKE_SECONDS = 5
        const val MAX_PENALTY_SECONDS = 60
        const val STRIKE_RESET_MS = 2 * 60 * 60_000L
    }
}
