package com.dhruv.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dhruv.focusguard.FocusGuardApp
import com.dhruv.focusguard.data.repository.TaskRepository

/**
 * Watches for foreground app changes. When a monitored app (e.g. Instagram)
 * comes to the foreground and the cooldown has passed, launches [OverlayActivity]
 * to show the user's task list with a countdown timer.
 */
class AppDetectorService : AccessibilityService() {

    private lateinit var repo: TaskRepository
    private var lastDetectedPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        repo = (application as FocusGuardApp).repository
        Log.d(TAG, "AppDetectorService created")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        // Ignore our own overlay and system UI
        if (packageName == applicationContext.packageName) return
        if (packageName == "com.android.systemui") return

        // Avoid duplicate triggers for the same app
        if (packageName == lastDetectedPackage) return
        lastDetectedPackage = packageName

        // Check if this app is monitored
        if (!repo.monitoredApps.contains(packageName)) return
        if (!repo.isInterceptionEnabled) return
        if (repo.isCooldownActive()) {
            Log.d(TAG, "Cooldown active, skipping overlay for $packageName")
            return
        }

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted")
            return
        }

        Log.d(TAG, "Monitored app detected: $packageName — launching overlay")
        repo.lastOverlayShownAt = System.currentTimeMillis()

        val intent = Intent(this, OverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_TRIGGER_APP, packageName)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AppDetectorService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AppDetectorService destroyed")
    }

    companion object {
        private const val TAG = "AppDetector"
        const val EXTRA_TRIGGER_APP = "trigger_app"
    }
}
