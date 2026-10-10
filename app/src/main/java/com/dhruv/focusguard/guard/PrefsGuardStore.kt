package com.dhruv.focusguard.guard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

/**
 * [GuardStore] over SharedPreferences. Writes use commit() rather than apply():
 * these values matter most exactly when the process is about to be killed, and
 * they change only a few times per Instagram visit.
 */
@SuppressLint("ApplySharedPref")
class PrefsGuardStore(context: Context) : GuardStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var strikes: Int
        get() = prefs.getInt(KEY_STRIKES, 0)
        set(value) { prefs.edit().putInt(KEY_STRIKES, value).commit() }

    override var lastStrikeAt: Long
        get() = prefs.getLong(KEY_LAST_STRIKE, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_STRIKE, value).commit() }

    override var abandonedAt: Long
        get() = prefs.getLong(KEY_ABANDONED, 0L)
        set(value) { prefs.edit().putLong(KEY_ABANDONED, value).commit() }

    override var timerStartedAt: Long
        get() = prefs.getLong(KEY_TIMER_STARTED, 0L)
        set(value) { prefs.edit().putLong(KEY_TIMER_STARTED, value).commit() }

    /** Wall-clock millis until which the tamper lock is suspended (maintenance window). */
    var tamperUnlockedUntil: Long
        get() = prefs.getLong(KEY_TAMPER_UNTIL, 0L)
        set(value) { prefs.edit().putLong(KEY_TAMPER_UNTIL, value).commit() }

    fun isTamperUnlocked(nowWall: Long = System.currentTimeMillis()): Boolean =
        tamperUnlockedUntil > nowWall

    /** True while we have muted the media stream; lets a restarted service undo it. */
    var mutedByUs: Boolean
        get() = prefs.getBoolean(KEY_MUTED, false)
        set(value) { prefs.edit().putBoolean(KEY_MUTED, value).commit() }

    // ── Today's numbers (reset at local midnight) ───────────────

    /** Times Instagram was opened (= pauses shown) today. */
    val opensToday: Int get() = todayInt(KEY_OPENS)

    /** Times the user chose not to continue (closed, breathed, focused) today. */
    val walkAwaysToday: Int get() = todayInt(KEY_WALKAWAYS)

    /** Focus-sprint minutes completed from the pause screen today. */
    val focusMinutesToday: Int get() = todayInt(KEY_FOCUS_MIN)

    fun recordOpen() = bumpToday(KEY_OPENS, 1)
    fun recordWalkAway() = bumpToday(KEY_WALKAWAYS, 1)
    fun recordFocusMinutes(minutes: Int) = bumpToday(KEY_FOCUS_MIN, minutes)

    private fun todayKey(): String =
        java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    private fun todayInt(key: String): Int =
        if (prefs.getString(KEY_DAY, "") == todayKey()) prefs.getInt(key, 0) else 0

    private fun bumpToday(key: String, by: Int) {
        val today = todayKey()
        val e = prefs.edit()
        if (prefs.getString(KEY_DAY, "") != today) {
            e.putString(KEY_DAY, today)
                .putInt(KEY_OPENS, 0)
                .putInt(KEY_WALKAWAYS, 0)
                .putInt(KEY_FOCUS_MIN, 0)
            e.putInt(key, by)
        } else {
            e.putInt(key, prefs.getInt(key, 0) + by)
        }
        e.apply()
    }

    companion object {
        private const val FILE = "focusguard_guard"
        private const val KEY_STRIKES = "strikes"
        private const val KEY_LAST_STRIKE = "last_strike_at"
        private const val KEY_ABANDONED = "abandoned_at"
        private const val KEY_TIMER_STARTED = "timer_started_at"
        private const val KEY_TAMPER_UNTIL = "tamper_unlocked_until"
        private const val KEY_MUTED = "muted_by_us"
        private const val KEY_DAY = "stats_day"
        private const val KEY_OPENS = "stats_opens"
        private const val KEY_WALKAWAYS = "stats_walkaways"
        private const val KEY_FOCUS_MIN = "stats_focus_min"

        /** How long a maintenance unlock lasts. */
        const val TAMPER_UNLOCK_MS = 5 * 60_000L
    }
}
