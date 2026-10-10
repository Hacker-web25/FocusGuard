package com.dhruv.focusguard.guard

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.dhruv.focusguard.service.AppDetectorService

/**
 * Status checks and self-repair for the accessibility service.
 *
 * Self-repair needs WRITE_SECURE_SETTINGS, which only adb can grant:
 *   adb shell pm grant com.dhruv.focusguard android.permission.WRITE_SECURE_SETTINGS
 * With it, the app can switch its own service back on after it has been killed.
 */
object Protection {

    private const val TAG = "FocusGuard"

    const val ADB_GRANT_COMMAND =
        "adb shell pm grant com.dhruv.focusguard android.permission.WRITE_SECURE_SETTINGS"

    fun component(context: Context) = ComponentName(context, AppDetectorService::class.java)

    /** True when the system has the service bound and running right now. */
    fun isRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val mine = component(context)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val si = it.resolveInfo?.serviceInfo ?: return@any false
                si.packageName == mine.packageName && si.name == mine.className
            }
    }

    /** True when the service is switched on in Settings (it may still have crashed). */
    fun isEnabledInSettings(context: Context): Boolean {
        val mine = component(context)
        return enabledComponents(context).any { it == mine }
    }

    fun canSelfHeal(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isXiaomi(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
    }

    /**
     * If the service is not running and we hold WRITE_SECURE_SETTINGS, switch it
     * off and on again. The off step clears the system's "crashed" mark for the
     * service; the on step makes it bind fresh.
     *
     * @return true if a repair was attempted.
     */
    fun healIfNeeded(context: Context, onDone: (() -> Unit)? = null): Boolean {
        if (isRunning(context) || !canSelfHeal(context)) {
            onDone?.invoke()
            return false
        }
        val app = context.applicationContext
        val resolver = app.contentResolver
        val mine = component(app)
        val others = enabledComponents(app).filter { it != mine }

        return try {
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                others.joinToString(":") { it.flattenToString() }
            )
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Settings.Secure.putString(
                        resolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        (others + mine).joinToString(":") { it.flattenToString() }
                    )
                    Settings.Secure.putString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                    Log.i(TAG, "Re-enabled accessibility service")
                } catch (e: SecurityException) {
                    Log.w(TAG, "Re-enable failed", e)
                } finally {
                    onDone?.invoke()
                }
            }, 700)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Self-heal not permitted", e)
            onDone?.invoke()
            false
        }
    }

    private fun enabledComponents(context: Context): List<ComponentName> {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return emptyList()
        return raw.split(':')
            .filter { it.isNotBlank() }
            .mapNotNull { ComponentName.unflattenFromString(it) }
    }
}
