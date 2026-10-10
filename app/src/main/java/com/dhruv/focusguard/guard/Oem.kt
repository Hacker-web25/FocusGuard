package com.dhruv.focusguard.guard

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.dhruv.focusguard.service.AppDetectorService

/**
 * Phone-maker specifics. Every brand hides "autostart" and "battery" in a
 * different place, so each action is a list of known screens tried in order,
 * always ending in a screen every Android phone has (App info).
 */
object Oem {

    enum class Brand(val label: String, val hasAutostart: Boolean) {
        XIAOMI("Xiaomi", true),
        SAMSUNG("Samsung", false),
        OPPO("Oppo", true),
        REALME("realme", true),
        ONEPLUS("OnePlus", true),
        VIVO("vivo", true),
        HUAWEI("Huawei", true),
        HONOR("Honor", true),
        ASUS("ASUS", true),
        NOKIA("Nokia", false),
        MOTOROLA("Motorola", false),
        GOOGLE("Pixel", false),
        OTHER("Android", false)
    }

    val brand: Brand by lazy {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> Brand.XIAOMI
            "samsung" in m -> Brand.SAMSUNG
            "oneplus" in m -> Brand.ONEPLUS
            "realme" in m -> Brand.REALME
            "oppo" in m -> Brand.OPPO
            listOf("vivo", "iqoo").any { it in m } -> Brand.VIVO
            "honor" in m -> Brand.HONOR
            "huawei" in m -> Brand.HUAWEI
            "asus" in m -> Brand.ASUS
            listOf("nokia", "hmd").any { it in m } -> Brand.NOKIA
            listOf("motorola", "moto", "lenovo").any { it in m } -> Brand.MOTOROLA
            "google" in m -> Brand.GOOGLE
            else -> Brand.OTHER
        }
    }

    /** Android 13+ blocks accessibility for apps installed from a file until allowed. */
    val needsRestrictedSettings: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // ── Status checks ───────────────────────────────────────────

    /**
     * True when "Allow restricted settings" is on (or not needed). Reads the
     * system's own switch for it; returns null if this phone won't say.
     */
    fun restrictedSettingsAllowed(context: Context): Boolean? {
        if (!needsRestrictedSettings) return true
        if (Protection.isRunning(context) || Protection.isEnabledInSettings(context)) return true
        return try {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return null
            val mode = ops.unsafeCheckOpNoThrow(
                "android:access_restricted_settings", Process.myUid(), context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
        } catch (e: Exception) {
            null
        }
    }

    // ── Openers ─────────────────────────────────────────────────

    fun appInfo(context: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )

    /** Straight to FocusGuard's own switch where the phone supports it, else the list. */
    fun openAccessibility(context: Context) {
        val detail = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, AppDetectorService::class.java).flattenToString())
        openFirst(context, listOf(detail, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
    }

    fun openRestrictedSettings(context: Context) = openFirst(context, listOf(appInfo(context)))

    /**
     * Android's own "let this app run in the background" prompt first; brand
     * screens with stronger battery savers next; App info last.
     */
    fun openBattery(context: Context, preferBrandScreen: Boolean) {
        val pkg = context.packageName
        val system = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
        val brandScreens = when (brand) {
            Brand.XIAOMI -> listOf(
                Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
                    .putExtra("package_name", pkg)
                    .putExtra("package_label", "FocusGuard"),
                component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                    .putExtra("package_name", pkg)
                    .putExtra("package_label", "FocusGuard")
            )
            Brand.SAMSUNG -> listOf(
                component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                component("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")
            )
            Brand.HUAWEI -> listOf(
                component("com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity")
            )
            Brand.NOKIA -> listOf(
                component("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity")
            )
            else -> emptyList()
        }
        val order = if (preferBrandScreen) brandScreens + system else listOf(system) + brandScreens
        openFirst(context, order + appInfo(context))
    }

    fun openAutostart(context: Context) {
        val list = when (brand) {
            Brand.XIAOMI -> listOf(
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            Brand.OPPO, Brand.REALME, Brand.ONEPLUS -> listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                component("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
                component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            )
            Brand.VIVO -> listOf(
                component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
            )
            Brand.HUAWEI -> listOf(
                component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
            )
            Brand.HONOR -> listOf(
                component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            )
            Brand.ASUS -> listOf(
                component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                component("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")
            )
            else -> emptyList()
        }
        openFirst(context, list + appInfo(context))
    }

    // ── Plain-language steps for this phone ─────────────────────

    fun restrictedSteps(): List<String> = listOf(
        "Tap “Open Pause screen settings” below once and try to turn FocusGuard on. Android will block it. That’s expected.",
        "Come back and tap “Open App info”.",
        "Tap ⋮ in the top-right corner, then “Allow restricted settings”.",
        "Confirm with your PIN or fingerprint."
    )

    fun accessibilitySteps(): List<String> = when (brand) {
        Brand.SAMSUNG -> listOf("Tap “Installed apps”.", "Tap FocusGuard and turn it on.", "Tap “Allow”.")
        Brand.XIAOMI -> listOf("Tap “Downloaded apps”.", "Tap FocusGuard and turn it on.", "Wait for the timer, then tap “OK”.")
        Brand.VIVO -> listOf("Tap “Downloaded services” (or “Installed services”).", "Tap FocusGuard and turn it on.", "Tap “Allow”.")
        Brand.OPPO, Brand.REALME, Brand.ONEPLUS -> listOf("Tap “Downloaded apps”.", "Tap FocusGuard and turn it on.", "Tap “Allow”.")
        Brand.HUAWEI, Brand.HONOR -> listOf("Find FocusGuard under “Downloaded services”.", "Turn it on and tap “OK”.")
        else -> listOf("Tap “Downloaded apps” (sometimes “Installed services”).", "Tap FocusGuard and turn it on.", "Tap “Allow”.")
    }

    fun batterySteps(): List<String> = when (brand) {
        Brand.XIAOMI -> listOf(
            "Choose “No restrictions”.",
            "If Android first asks “Let app always run in background?”, tap “Allow”."
        )
        Brand.SAMSUNG -> listOf(
            "Tap “Allow” on the prompt.",
            "Then in App info → Battery, choose “Unrestricted”."
        )
        Brand.OPPO, Brand.REALME, Brand.ONEPLUS -> listOf(
            "Tap “Allow” on the prompt.",
            "Then in App info → Battery usage, turn on “Allow background activity”."
        )
        Brand.VIVO -> listOf(
            "Tap “Allow” on the prompt.",
            "Then in Battery → Background power consumption, allow FocusGuard."
        )
        Brand.HUAWEI, Brand.HONOR -> listOf(
            "Tap “Allow” on the prompt.",
            "In App launch, switch FocusGuard to “Manage manually” and turn everything on."
        )
        else -> listOf("Android asks “Let app always run in background?”. Tap “Allow”.")
    }

    fun autostartSteps(): List<String> = when (brand) {
        Brand.XIAOMI -> listOf("Find FocusGuard in the list.", "Turn its switch on.")
        Brand.OPPO, Brand.REALME, Brand.ONEPLUS -> listOf("Find FocusGuard under “Auto launch” or “Startup manager”.", "Turn it on.")
        Brand.VIVO -> listOf("Find FocusGuard under “Background startup” or “Autostart”.", "Turn it on.")
        Brand.HUAWEI, Brand.HONOR -> listOf("Find FocusGuard in “App launch”.", "Switch to “Manage manually” and turn on Auto-launch.")
        Brand.ASUS -> listOf("Open “Auto-start manager”.", "Allow FocusGuard.")
        else -> listOf("Your phone starts apps normally. Nothing to do here.")
    }

    // ── helpers ─────────────────────────────────────────────────

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    /** Try each screen until one opens. Returns false if none did. */
    fun openFirst(context: Context, intents: List<Intent>): Boolean {
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                // try the next one
            } catch (e: SecurityException) {
                // exists but not exported on this build; try the next one
            } catch (e: Exception) {
                // be defensive with OEM screens
            }
        }
        return false
    }
}
