package com.dhruv.focusguard.guard

/**
 * Decides whether a screen is one that could switch FocusGuard off: its
 * accessibility toggle, App info (Force stop / Uninstall / Clear data), or an
 * uninstall dialog.
 *
 * This is friction, not a guarantee: OEM settings wording varies, and a
 * determined user can always get around an app they own. The checks are kept
 * narrow so ordinary settings pages (app lists, the accessibility list itself)
 * stay usable.
 */
object TamperRules {

    private const val APP = "focusguard"

    /** Packages whose screens can disable, stop or uninstall us. */
    val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.miui.securitycenter",
        "com.xiaomi.misettings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller"
    )

    /** Launchers can show "Uninstall FocusGuard?" dialogs or drop targets. */
    val LAUNCHER_PACKAGES = setOf(
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher"
    )

    val WATCHED_PACKAGES = SETTINGS_PACKAGES + LAUNCHER_PACKAGES

    private val SETTINGS_KEYWORDS = listOf(
        "force stop",
        "uninstall",
        "use focusguard",
        "stop focusguard",
        "turn off focusguard",
        "focusguard shortcut",
        "clear data",
        "clear storage",
        "clear all data"
    )

    /** @param texts lower-cased text and content descriptions visible in the window. */
    fun isTamperScreen(packageName: String, texts: List<String>): Boolean {
        if (packageName in LAUNCHER_PACKAGES) {
            // Home screen shows our icon label all the time; only block an actual
            // uninstall prompt naming us.
            return texts.any { it.contains(APP) && it.contains("uninstall") }
        }
        if (packageName !in SETTINGS_PACKAGES) return false
        if (texts.none { it.contains(APP) }) return false
        return texts.any { t -> SETTINGS_KEYWORDS.any { t.contains(it) } }
    }
}
