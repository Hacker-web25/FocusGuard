package com.dhruv.focusguard.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.guard.GuardEngine
import com.dhruv.focusguard.guard.PrefsGuardStore
import com.dhruv.focusguard.guard.Protection
import com.dhruv.focusguard.ui.components.MindfulCard
import com.dhruv.focusguard.ui.components.MindfulField
import com.dhruv.focusguard.ui.components.RowDivider
import com.dhruv.focusguard.ui.components.ScreenHeader
import com.dhruv.focusguard.ui.components.SectionLabel
import com.dhruv.focusguard.ui.components.SettingsRow
import com.dhruv.focusguard.ui.components.openAccessibilitySettings
import com.dhruv.focusguard.ui.components.rememberProtectionState
import com.dhruv.focusguard.ui.components.rememberResumeTick
import com.dhruv.focusguard.ui.theme.Sizes
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.delay

/**
 * Settings. The guard is always on, so there is no on/off switch and no
 * cooldown: only the pause length, your name, status, and a deliberate
 * maintenance unlock for updating or uninstalling.
 */
@Composable
fun SettingsScreen(viewModel: TaskViewModel) {
    val c = MaterialTheme.mindful
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state = rememberProtectionState()
    val resume = rememberResumeTick()

    val store = remember { PrefsGuardStore(context) }
    var timerDuration by remember { mutableIntStateOf(viewModel.timerDuration) }
    var name by remember { mutableStateOf(viewModel.userName) }
    var showUnlock by remember { mutableStateOf(false) }

    // Refresh penalty / unlock countdown once a second while visible
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(resume) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val penalty = remember(now) { GuardEngine(store).currentPenaltySeconds(now) }
    val unlockLeftMs = (store.tamperUnlockedUntil - now).coerceAtLeast(0)
    val chevron: @Composable RowScope.() -> Unit = { ChevronIcon() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(top = Space.md, bottom = Sizes.bottomBarClearance)
        ) {
            ScreenHeader(
                title = "Settings",
                subtitle = "FocusGuard is always on. Every Instagram open gets a pause."
            )

            Spacer(Modifier.height(Space.lg))

            // ── Protection ─────────────────────────────────────
            SectionLabel("Protection")
            Spacer(Modifier.height(Space.xs))
            MindfulCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                SettingsRow(
                    icon = if (state.running) Icons.Filled.Lock else Icons.Filled.Warning,
                    title = if (state.running) "Guard is running" else "Guard is OFF",
                    subtitle = if (state.running) {
                        "Instagram is paused every time it opens"
                    } else {
                        "Tap to turn FocusGuard on in Accessibility"
                    },
                    iconTint = if (state.running) c.accentDeep else c.chipUrgentInk,
                    iconBackground = if (state.running) c.accentSoft else c.chipUrgent,
                    onClick = if (state.running) null else ({ context.openAccessibilitySettings() }),
                    trailing = if (state.running) null else chevron
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.Refresh,
                    title = "Auto-repair",
                    subtitle = if (state.canSelfHeal) {
                        "On. FocusGuard switches itself back on if it's killed"
                    } else {
                        "Off. Tap to copy the one-time adb command"
                    },
                    iconTint = c.chipMediumInk,
                    iconBackground = c.chipMedium,
                    onClick = if (state.canSelfHeal) null else ({
                        clipboard.setText(AnnotatedString(Protection.ADB_GRANT_COMMAND))
                        Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                    })
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.Warning,
                    title = "Skip penalty",
                    subtitle = if (penalty > 0) {
                        "+${penalty}s on your next pause. Sit through it once and it's gone"
                    } else {
                        "None. Leaving a pause early adds +5s to the next one (max +60s)"
                    },
                    iconTint = c.chipHighInk,
                    iconBackground = c.chipHigh
                )
            }

            Spacer(Modifier.height(Space.md))

            // ── The pause ──────────────────────────────────────
            SectionLabel("The pause")
            Spacer(Modifier.height(Space.xs))
            MindfulCard(modifier = Modifier.fillMaxWidth()) {
                SliderSetting(
                    title = "Pause length",
                    valueLabel = "${timerDuration}s",
                    description = "How long you sit with your tasks before Instagram opens.",
                    value = timerDuration.toFloat(),
                    valueRange = 15f..180f,
                    steps = 10,
                    onValueChange = { timerDuration = it.toInt() },
                    onValueChangeFinished = { viewModel.timerDuration = timerDuration }
                )
            }

            Spacer(Modifier.height(Space.md))

            // ── You ────────────────────────────────────────────
            SectionLabel("You")
            Spacer(Modifier.height(Space.xs))
            MindfulCard(modifier = Modifier.fillMaxWidth()) {
                MindfulField(
                    label = "Your name",
                    value = name,
                    onValueChange = {
                        name = it
                        viewModel.userName = it
                    },
                    placeholder = "Your first name"
                )
            }

            Spacer(Modifier.height(Space.md))

            // ── Maintenance ────────────────────────────────────
            SectionLabel("Maintenance")
            Spacer(Modifier.height(Space.xs))
            MindfulCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                SettingsRow(
                    icon = Icons.Filled.Build,
                    title = if (unlockLeftMs > 0) "Settings unlocked" else "Unlock FocusGuard's settings",
                    subtitle = if (unlockLeftMs > 0) {
                        "You can change, update or uninstall FocusGuard for ${formatMs(unlockLeftMs)}"
                    } else {
                        "Needed to update, uninstall or switch off FocusGuard. Takes a 60-second wait."
                    },
                    iconTint = c.textSecondary,
                    iconBackground = c.surfaceSunken,
                    onClick = if (unlockLeftMs > 0) null else ({ showUnlock = true }),
                    trailing = if (unlockLeftMs > 0) null else chevron
                )
            }

            Spacer(Modifier.height(Space.lg))
        }
    }

    if (showUnlock) {
        UnlockDialog(
            onDismiss = { showUnlock = false },
            onUnlocked = {
                store.tamperUnlockedUntil = System.currentTimeMillis() + PrefsGuardStore.TAMPER_UNLOCK_MS
                now = System.currentTimeMillis()
                showUnlock = false
            }
        )
    }
}

/**
 * A deliberate 60-second wait before the tamper lock lifts. Leaving the app
 * resets it.
 */
@Composable
private fun UnlockDialog(onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val c = MaterialTheme.mindful
    val resume = rememberResumeTick()
    var secondsLeft by remember(resume) { mutableIntStateOf(UNLOCK_WAIT_SECONDS) }

    LaunchedEffect(resume) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Unlock FocusGuard's settings?", color = c.textPrimary) },
        text = {
            Text(
                if (secondsLeft > 0) {
                    "Wait ${secondsLeft}s. Leaving this screen restarts the wait. " +
                        "Once unlocked you have 5 minutes to update, uninstall or change FocusGuard."
                } else {
                    "Unlocked for 5 minutes once you confirm."
                },
                color = c.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onUnlocked, enabled = secondsLeft == 0) {
                Text("Unlock", color = if (secondsLeft == 0) c.accentDeep else c.disabledText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) }
        }
    )
}

private const val UNLOCK_WAIT_SECONDS = 60

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun SliderSetting(
    title: String,
    valueLabel: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val c = MaterialTheme.mindful
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = c.accentDeep
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = c.accentDeep,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.surfaceSunken,
                activeTickColor = c.accentDeep,
                inactiveTickColor = c.border
            )
        )
    }
}

@Composable
private fun ChevronIcon() {
    val c = MaterialTheme.mindful
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = c.textSecondary,
        modifier = Modifier.size(Sizes.icon)
    )
}
