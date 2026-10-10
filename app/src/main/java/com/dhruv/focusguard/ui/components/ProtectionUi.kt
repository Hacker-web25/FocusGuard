package com.dhruv.focusguard.ui.components

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dhruv.focusguard.guard.Protection
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import kotlinx.coroutines.delay

/** Snapshot of everything that keeps the guard alive. */
data class ProtectionState(
    val running: Boolean,
    val canSelfHeal: Boolean,
    val batteryExempt: Boolean
)

private fun readProtection(context: Context) = ProtectionState(
    running = Protection.isRunning(context),
    canSelfHeal = Protection.canSelfHeal(context),
    batteryExempt = Protection.isIgnoringBatteryOptimizations(context)
)

/** Increments every time the host activity resumes (e.g. back from Settings). */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

/** Live protection status: refreshed on resume and every 1.5s while shown. */
@Composable
fun rememberProtectionState(): ProtectionState {
    val context = LocalContext.current
    val resume = rememberResumeTick()
    var state by remember { mutableStateOf(readProtection(context)) }
    LaunchedEffect(resume) {
        while (true) {
            state = readProtection(context)
            delay(1_500)
        }
    }
    return state
}

/** Red strip shown above the main tabs whenever the guard is not running. */
@Composable
fun ProtectionBanner(onFix: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.mindful
    MindfulCard(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.gutter, vertical = Space.xs),
        onClick = onFix,
        background = c.chipUrgent,
        borderColor = c.danger,
        elevated = false,
        contentPadding = Space.sm
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = c.chipUrgentInk)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    "Protection is OFF",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.chipUrgentInk
                )
                Text(
                    "Instagram is not being paused. Tap to turn FocusGuard back on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.chipUrgentInk
                )
            }
        }
    }
}

// ── Intents to the system screens we send people to ─────────────

fun Context.safeStart(intent: Intent, fallback: Intent? = null) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        fallback?.let { safeStart(it) }
    } catch (e: SecurityException) {
        fallback?.let { safeStart(it) }
    }
}

fun Context.appDetailsIntent() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

fun Context.openAccessibilitySettings() =
    safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

fun Context.openAppDetails() = safeStart(appDetailsIntent())

@android.annotation.SuppressLint("BatteryLife")
fun Context.requestBatteryExemption() = safeStart(
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
    fallback = appDetailsIntent()
)

fun Context.openMiuiAutostart() = safeStart(
    Intent().setComponent(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
    ),
    fallback = appDetailsIntent()
)
