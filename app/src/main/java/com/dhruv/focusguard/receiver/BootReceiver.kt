package com.dhruv.focusguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * AccessibilityServices survive reboot automatically once enabled,
 * so this receiver is a no-op safety net for logging.
 * If you later add a foreground service, start it here.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("FocusGuard", "Boot completed — accessibility service should auto-restart")
        }
    }
}
