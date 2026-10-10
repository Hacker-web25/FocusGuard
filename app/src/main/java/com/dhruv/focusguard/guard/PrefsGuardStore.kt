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

    companion object {
        private const val FILE = "focusguard_guard"
        private const val KEY_STRIKES = "strikes"
        private const val KEY_LAST_STRIKE = "last_strike_at"
        private const val KEY_ABANDONED = "abandoned_at"
        private const val KEY_TIMER_STARTED = "timer_started_at"
        private const val KEY_TAMPER_UNTIL = "tamper_unlocked_until"

        /** How long a maintenance unlock lasts. */
        const val TAMPER_UNLOCK_MS = 5 * 60_000L
    }
}
