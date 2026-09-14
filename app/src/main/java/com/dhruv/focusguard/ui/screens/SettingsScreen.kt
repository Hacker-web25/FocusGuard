package com.dhruv.focusguard.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TaskViewModel) {
    val context = LocalContext.current
    var timerDuration by remember { mutableIntStateOf(viewModel.timerDuration) }
    var cooldown by remember { mutableIntStateOf(viewModel.cooldownMinutes) }
    var enabled by remember { mutableStateOf(viewModel.isInterceptionEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Master toggle ──────────────────────────────────
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Interception Active", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Show task overlay when opening monitored apps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            viewModel.isInterceptionEnabled = it
                        }
                    )
                }
            }

            // ── Timer duration ─────────────────────────────────
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Timer Duration", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${timerDuration}s — how long you review tasks before dismissing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = timerDuration.toFloat(),
                        onValueChange = { timerDuration = it.toInt() },
                        onValueChangeFinished = { viewModel.timerDuration = timerDuration },
                        valueRange = 15f..180f,
                        steps = 10
                    )
                }
            }

            // ── Cooldown ───────────────────────────────────────
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cooldown Period", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${cooldown} min — won't trigger again within this window",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = cooldown.toFloat(),
                        onValueChange = { cooldown = it.toInt() },
                        onValueChangeFinished = { viewModel.cooldownMinutes = cooldown },
                        valueRange = 1f..30f,
                        steps = 28
                    )
                }
            }

            // ── Permissions ────────────────────────────────────
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Permissions", style = MaterialTheme.typography.titleMedium)

                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Accessibility Settings")
                    }

                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Overlay Permission")
                    }

                    Text(
                        "Both permissions must be granted for the interceptor to work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
