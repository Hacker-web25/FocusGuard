package com.dhruv.focusguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dhruv.focusguard.guard.Protection
import com.dhruv.focusguard.guard.Watchdog

/**
 * After boot or an app update, start the watchdog and repair the accessibility
 * service if it did not come back on its own.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                Watchdog.schedule(context)
                Protection.healIfNeeded(context) { pending.finish() }
            }
        }
    }
}
