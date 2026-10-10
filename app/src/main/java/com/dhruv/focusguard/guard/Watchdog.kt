package com.dhruv.focusguard.guard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Once a minute (while the phone is awake) checks that the accessibility
 * service is alive and repairs it if we have WRITE_SECURE_SETTINGS.
 *
 * Covers the case where the process was killed but not force-stopped. A real
 * force-stop cancels alarms too; then the next launch of FocusGuard repairs it.
 */
object Watchdog {

    private const val INTERVAL_MS = 60_000L
    private const val REQUEST_CODE = 4201

    fun schedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            Intent(app, WatchdogReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Non-wakeup, inexact: no need to wake a sleeping phone, Instagram can't open then.
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + INTERVAL_MS, pi)
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Protection.healIfNeeded(context) {
            Watchdog.schedule(context)
            pending.finish()
        }
    }
}
