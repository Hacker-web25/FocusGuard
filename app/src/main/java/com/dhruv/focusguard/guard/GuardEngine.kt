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

    // ── Usage budget ────────────────────────────────────────────

    /** Milliseconds actually spent inside the monitored app today. */
    var usedTodayMs: Long
    /** Milliseconds spent in the current unbroken run of use. */
    var sessionMs: Long
    /** Wall-clock of the last moment usage was counted (0 = never). */
    var lastUsageAt: Long
    /** Wall-clock when the current visit started; set while in use so a kill still bills it. */
    var usageOpenAt: Long
    /** Wall-clock until which the app is in its cool-down (0 = not cooling down). */
    var cooldownUntil: Long

    /** Clear today's totals if the calendar day has moved on since they were written. */
    fun rollOverIfNewDay(nowWall: Long)
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
 * On top of the pause there are two hard limits, both optional:
 *
 *  - **Daily budget.** Time actually spent inside the app is counted; once
 *    [Limits.dailyLimitMinutes] is used up, the app does not open again until
 *    local midnight.
 *  - **Session cool-down.** [Limits.sessionLimitMinutes] of unbroken use earns
 *    a [Limits.cooldownMinutes] cool-down. A real break away from the app
 *    ([SESSION_RESET_MS]) starts the session count again from zero, so a quick
 *    glance at WhatsApp doesn't dodge it.
 *
 * Time-sensitive comparisons for the session grace use elapsed-realtime (immune to
 * clock changes); persisted values use wall-clock so they survive reboots.
 */
class GuardEngine(private val store: GuardStore) {

    /** The user's settings, passed in on each call so changes take effect at once. */
    data class Limits(
        val baseSeconds: Int,
        /** 0 = no daily budget. */
        val dailyLimitMinutes: Int = 0,
        /** 0 = no cool-down. */
        val sessionLimitMinutes: Int = 0,
        val cooldownMinutes: Int = DEFAULT_COOLDOWN_MINUTES
    )

    enum class BlockReason { DailyLimit, Cooldown }

    sealed interface Decision {
        data object None : Decision
        data object Hide : Decision
        data class Show(
            val totalSeconds: Int,
            val strikes: Int,
            val penaltySeconds: Int,
            val justPenalized: Boolean
        ) : Decision

        /** Instagram is not opening at all. [untilWall] is when it will again. */
        data class Block(
            val reason: BlockReason,
            val untilWall: Long,
            val usedMinutes: Int,
            val limitMinutes: Int
        ) : Decision
    }

    /** Is the monitored app currently the top app? */
    var inTarget = false
        private set

    /** Is the pause overlay currently on screen? */
    var lockShowing = false
        private set

    /** Is the "not right now" block screen currently on screen? */
    var blockShowing = false
        private set

    /** Wall-clock when the block on screen lifts (0 = nothing blocked). */
    var blockUntilWall = 0L
        private set

    /** Has the countdown finished for the current visit? */
    var unlocked = false
        private set

    /** Elapsed-realtime when the user left an unlocked session (0 = not left). */
    private var leftUnlockedAt = 0L

    /** User picked a diversion during this pause; leaving now is not penalised. */
    private var diverted = false

    /** Elapsed-realtime when the current spell of real use began (0 = not using). */
    private var usingSince = 0L

    /**
     * Right after "Close Instagram" we send the user Home, but for a few frames
     * Instagram is still the top app. Until something else is seen on top (or
     * this time passes), Instagram readings are ignored so the pause doesn't
     * flash back open and count a fake skip.
     */
    private var suppressTargetUntil = 0L

    /**
     * Call once when the service (re)connects. If a pause was running when the
     * process died, the user most likely killed us to get past it: treat that as
     * walking away, so the next open is penalised. Any use that was being timed
     * when we died is billed to the budget, so force-stopping can't buy free time.
     */
    fun recoverAfterRestart(nowWall: Long) {
        val started = store.timerStartedAt
        if (started > 0) {
            store.abandonedAt = started
            store.timerStartedAt = 0
        }
        val openedAt = store.usageOpenAt
        if (openedAt > 0) {
            val unbilled = (nowWall - openedAt).coerceIn(0L, MAX_RECOVERED_USE_MS)
            if (unbilled > 0) addUsage(unbilled, nowWall)
            store.usageOpenAt = 0
        }
        inTarget = false
        lockShowing = false
        blockShowing = false
        blockUntilWall = 0L
        unlocked = false
        diverted = false
        leftUnlockedAt = 0
        usingSince = 0
    }

    /**
     * The top app changed (or was re-checked).
     *
     * @param isTarget whether the new top app is a monitored app.
     */
    fun onForeground(isTarget: Boolean, limits: Limits, nowElapsed: Long, nowWall: Long): Decision {
        if (suppressTargetUntil != 0L) {
            if (!isTarget || nowElapsed >= suppressTargetUntil) {
                suppressTargetUntil = 0L
                if (isTarget) inTarget = false // timed out still in Instagram: lock as a fresh open
            } else {
                return Decision.None
            }
        }
        if (isTarget) {
            if (inTarget) {
                // Still inside Instagram. Self-heal if somehow nothing is on screen.
                if (lockShowing || blockShowing) return Decision.None
                if (unlocked) return checkLimits(limits, nowElapsed, nowWall)
                return startPause(limits, nowElapsed, nowWall)
            }
            inTarget = true

            if (unlocked && leftUnlockedAt > 0 && nowElapsed - leftUnlockedAt <= GRACE_MS) {
                // Quick switch away and back: keep the session, keep counting.
                leftUnlockedAt = 0
                beginUsage(nowElapsed, nowWall)
                return checkLimits(limits, nowElapsed, nowWall)
            }
            unlocked = false
            leftUnlockedAt = 0
            return startPause(limits, nowElapsed, nowWall)
        }

        // Some other app is on top.
        if (!inTarget) return Decision.None
        inTarget = false
        endUsage(nowElapsed, nowWall)

        if (blockShowing) {
            blockShowing = false
            blockUntilWall = 0L
            return Decision.Hide
        }

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
     * The block's own countdown has run out. If the user is still sitting in
     * front of it, the app opens the only way it ever does: with a pause.
     */
    fun onBlockExpired(limits: Limits, nowElapsed: Long, nowWall: Long): Decision {
        if (!blockShowing) return Decision.None
        if (blockedReason(limits, nowWall) != null) return Decision.None // still blocked
        blockShowing = false
        blockUntilWall = 0L
        return if (inTarget) startPause(limits, nowElapsed, nowWall) else Decision.Hide
    }

    /**
     * Called every few seconds while the user is actually inside the app, so a
     * budget that runs out mid-scroll takes effect there and then.
     */
    fun onUsageTick(limits: Limits, nowElapsed: Long, nowWall: Long): Decision {
        if (!inTarget || !unlocked || lockShowing || blockShowing) return Decision.None
        flushUsage(nowElapsed, nowWall)
        return checkLimits(limits, nowElapsed, nowWall)
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

    /** User tapped Continue after the countdown: the clock on their time starts now. */
    fun onContinue(nowElapsed: Long = 0L, nowWall: Long = System.currentTimeMillis()): Decision {
        if (!unlocked) return Decision.None
        lockShowing = false
        beginUsage(nowElapsed, nowWall)
        return Decision.Hide
    }

    /**
     * User tapped "Close Instagram". This is the behaviour we want, so it is never
     * penalised. The caller sends the user home.
     */
    fun onCloseTarget(nowElapsed: Long, nowWall: Long = System.currentTimeMillis()): Decision {
        endUsage(nowElapsed, nowWall)
        suppressTargetUntil = nowElapsed + CLOSE_SUPPRESS_MS
        lockShowing = false
        blockShowing = false
        blockUntilWall = 0L
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

    // ── Budget ──────────────────────────────────────────────────

    /** Minutes used today, rounded down. Rolls over by itself at midnight. */
    fun usedTodayMinutes(nowWall: Long): Int = (usedTodayMs(nowWall) / 60_000L).toInt()

    /** Milliseconds used today, including any spell still running. */
    fun usedTodayMs(nowWall: Long): Long {
        rollOverDay(nowWall)
        val open = store.usageOpenAt
        val live = if (open > 0) (nowWall - open).coerceAtLeast(0L) else 0L
        return store.usedTodayMs + live
    }

    /** Wall-clock the cool-down ends, or 0 when not cooling down. */
    fun cooldownUntil(nowWall: Long): Long =
        store.cooldownUntil.takeIf { it > nowWall } ?: 0L

    /**
     * How long until the next limit would bite, given what is already used.
     * [Long.MAX_VALUE] when no limit is set. Lets the service check at exactly
     * the right moment instead of polling hard.
     */
    fun msUntilNextLimit(limits: Limits, nowWall: Long): Long {
        var next = Long.MAX_VALUE
        if (limits.dailyLimitMinutes > 0) {
            next = minOf(next, limits.dailyLimitMinutes * 60_000L - usedTodayMs(nowWall))
        }
        if (limits.sessionLimitMinutes > 0) {
            next = minOf(next, limits.sessionLimitMinutes * 60_000L - currentSessionMs(nowWall))
        }
        return if (next == Long.MAX_VALUE) Long.MAX_VALUE else next.coerceAtLeast(0L)
    }

    /**
     * Is the app allowed open at all right now? Used by the UI; the service
     * goes through [onForeground], which applies the same rules.
     */
    fun blockedReason(limits: Limits, nowWall: Long): BlockReason? {
        if (store.cooldownUntil > nowWall) return BlockReason.Cooldown
        val budget = limits.dailyLimitMinutes
        if (budget > 0 && usedTodayMs(nowWall) >= budget * 60_000L) return BlockReason.DailyLimit
        return null
    }

    /**
     * Blocks when the budget is spent or a cool-down is running, and starts a
     * cool-down the moment one unbroken run gets too long.
     */
    private fun checkLimits(limits: Limits, nowElapsed: Long, nowWall: Long): Decision {
        rollOverDay(nowWall)

        if (store.cooldownUntil > nowWall) return block(BlockReason.Cooldown, limits, nowElapsed, nowWall)

        val budgetMs = limits.dailyLimitMinutes * 60_000L
        if (budgetMs > 0 && usedTodayMs(nowWall) >= budgetMs) {
            return block(BlockReason.DailyLimit, limits, nowElapsed, nowWall)
        }

        val sessionCapMs = limits.sessionLimitMinutes * 60_000L
        if (sessionCapMs > 0 && currentSessionMs(nowWall) >= sessionCapMs) {
            store.cooldownUntil = nowWall + limits.cooldownMinutes.coerceAtLeast(1) * 60_000L
            store.sessionMs = 0
            return block(BlockReason.Cooldown, limits, nowElapsed, nowWall)
        }
        return Decision.None
    }

    private fun block(reason: BlockReason, limits: Limits, nowElapsed: Long, nowWall: Long): Decision {
        endUsage(nowElapsed, nowWall)
        lockShowing = false
        blockShowing = true
        unlocked = false
        diverted = false
        leftUnlockedAt = 0
        store.timerStartedAt = 0
        store.abandonedAt = 0
        val until = when (reason) {
            BlockReason.Cooldown -> store.cooldownUntil
            BlockReason.DailyLimit -> nextMidnight(nowWall)
        }
        blockUntilWall = until
        return Decision.Block(
            reason = reason,
            untilWall = until,
            usedMinutes = usedTodayMinutes(nowWall),
            limitMinutes = limits.dailyLimitMinutes
        )
    }

    /**
     * Begin counting a spell of real use. A long enough gap since the last
     * one means the user genuinely stepped away, so the run starts over.
     */
    private fun beginUsage(nowElapsed: Long, nowWall: Long) {
        rollOverDay(nowWall)
        if (store.lastUsageAt > 0 && nowWall - store.lastUsageAt > SESSION_RESET_MS) store.sessionMs = 0
        usingSince = nowElapsed
        store.usageOpenAt = nowWall
    }

    /** Stop counting and bank what was used. */
    private fun endUsage(nowElapsed: Long, nowWall: Long) {
        flushUsage(nowElapsed, nowWall)
        usingSince = 0
        store.usageOpenAt = 0
    }

    /** Bank the time used so far without ending the spell. */
    private fun flushUsage(nowElapsed: Long, nowWall: Long) {
        val since = usingSince
        if (since <= 0L) return
        val delta = (nowElapsed - since).coerceIn(0L, MAX_RECOVERED_USE_MS)
        usingSince = nowElapsed
        store.usageOpenAt = nowWall
        if (delta > 0) addUsage(delta, nowWall)
    }

    /**
     * Bank [deltaMs] of use. Whether a break has happened is decided in
     * [beginUsage] alone: inside a spell the user has not been away, however
     * long it has been since the last check, so nothing is reset here.
     */
    private fun addUsage(deltaMs: Long, nowWall: Long) {
        rollOverDay(nowWall)
        store.usedTodayMs = store.usedTodayMs + deltaMs
        store.sessionMs = store.sessionMs + deltaMs
        store.lastUsageAt = nowWall
    }

    /** The current unbroken run, including any spell still open. */
    private fun currentSessionMs(nowWall: Long): Long {
        val open = store.usageOpenAt
        val live = if (open > 0) (nowWall - open).coerceAtLeast(0L) else 0L
        return store.sessionMs + live
    }

    /** Today's total resets by itself when the day changes. */
    private fun rollOverDay(nowWall: Long) = store.rollOverIfNewDay(nowWall)

    private fun startPause(limits: Limits, nowElapsed: Long, nowWall: Long): Decision {
        // A hard limit beats the pause: there is nothing to count down to.
        val blocked = checkLimits(limits, nowElapsed, nowWall)
        if (blocked is Decision.Block) return blocked

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
        blockShowing = false
        blockUntilWall = 0L
        unlocked = false
        diverted = false
        return Decision.Show(
            totalSeconds = limits.baseSeconds.coerceAtLeast(1) + penalty,
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
        const val CLOSE_SUPPRESS_MS = 2_500L
        const val ABANDON_WINDOW_MS = 10 * 60_000L
        const val STRIKE_SECONDS = 5
        const val MAX_PENALTY_SECONDS = 60
        const val STRIKE_RESET_MS = 2 * 60 * 60_000L

        /** A break this long starts the run of use again from zero. */
        const val SESSION_RESET_MS = 2 * 60_000L

        /** Longest single stretch we will bill from one reading (guards clock jumps). */
        const val MAX_RECOVERED_USE_MS = 60 * 60_000L

        const val DEFAULT_SESSION_LIMIT_MINUTES = 10
        const val DEFAULT_COOLDOWN_MINUTES = 5

        /** Local midnight after [nowWall], without pulling in Android types. */
        fun nextMidnight(nowWall: Long): Long {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = nowWall
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
