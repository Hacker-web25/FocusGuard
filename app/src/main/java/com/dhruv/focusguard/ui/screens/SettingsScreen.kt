package com.dhruv.focusguard.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.ui.components.MindfulCard
import com.dhruv.focusguard.ui.components.RowDivider
import com.dhruv.focusguard.ui.components.ScreenHeader
import com.dhruv.focusguard.ui.components.SectionLabel
import com.dhruv.focusguard.ui.components.SettingsRow
import com.dhruv.focusguard.ui.theme.PureWhite
import com.dhruv.focusguard.ui.theme.Sizes
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel

/**
 * Settings.
 *
 * Every control writes through the same view model properties as before, so
 * values still persist to SharedPreferences the moment they change.
 */
@Composable
fun SettingsScreen(viewModel: TaskViewModel) {
    val c = MaterialTheme.mindful
    val context = LocalContext.current

    var timerDuration by remember { mutableIntStateOf(viewModel.timerDuration) }
    var cooldown by remember { mutableIntStateOf(viewModel.cooldownMinutes) }
    var enabled by remember { mutableStateOf(viewModel.isInterceptionEnabled) }

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
                subtitle = "Shape how the pause works for you."
            )

            Spacer(Modifier.height(Space.lg))

            // ── The pause ──────────────────────────────────────
            SectionLabel("The pause")
            Spacer(Modifier.height(Space.xs))

            MindfulCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 0.dp
            ) {
                SettingsRow(
                    icon = Icons.Filled.Lock,
                    title = "Pause before distractions",
                    subtitle = "Show your tasks when a monitored app opens",
                    trailing = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                viewModel.isInterceptionEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = c.accentDeep,
                                checkedBorderColor = c.accentDeep,
                                uncheckedThumbColor = c.textSecondary,
                                uncheckedTrackColor = c.surfaceSunken,
                                uncheckedBorderColor = c.borderStrong
                            )
                        )
                    }
                )
            }

            Spacer(Modifier.height(Space.md))

            // ── Timing ─────────────────────────────────────────
            SectionLabel("Timing")
            Spacer(Modifier.height(Space.xs))

            MindfulCard(modifier = Modifier.fillMaxWidth()) {
                SliderSetting(
                    title = "Pause length",
                    valueLabel = "${timerDuration}s",
                    description = "How long you sit with your tasks before the app opens.",
                    value = timerDuration.toFloat(),
                    valueRange = 15f..180f,
                    steps = 10,
                    onValueChange = { timerDuration = it.toInt() },
                    onValueChangeFinished = { viewModel.timerDuration = timerDuration }
                )

                Spacer(Modifier.height(Space.md))

                SliderSetting(
                    title = "Cooldown",
                    valueLabel = "$cooldown min",
                    description = "Once you've paused, you won't be asked again for this long.",
                    value = cooldown.toFloat(),
                    valueRange = 1f..30f,
                    steps = 28,
                    onValueChange = { cooldown = it.toInt() },
                    onValueChangeFinished = { viewModel.cooldownMinutes = cooldown }
                )
            }

            Spacer(Modifier.height(Space.md))

            // ── Permissions ────────────────────────────────────
            SectionLabel("Permissions")
            Spacer(Modifier.height(Space.xs))

            MindfulCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 0.dp
            ) {
                SettingsRow(
                    icon = Icons.Filled.Build,
                    title = "Accessibility service",
                    subtitle = "Lets FocusGuard notice when a monitored app opens",
                    iconTint = c.chipHighInk,
                    iconBackground = c.chipHigh,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    },
                    trailing = { ChevronIcon() }
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "Display over other apps",
                    subtitle = "Lets the pause screen appear above the app",
                    iconTint = c.chipMediumInk,
                    iconBackground = c.chipMedium,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    },
                    trailing = { ChevronIcon() }
                )
            }

            Spacer(Modifier.height(Space.xs))
            Text(
                text = "Both permissions need to be granted for the pause to appear.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = Space.xxs)
            )

            Spacer(Modifier.height(Space.lg))
        }
    }
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
