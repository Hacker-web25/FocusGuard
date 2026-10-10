package com.dhruv.focusguard.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.guard.GuardEngine
import com.dhruv.focusguard.guard.PrefsGuardStore
import com.dhruv.focusguard.guard.Protection
import com.dhruv.focusguard.ui.components.Calm
import com.dhruv.focusguard.ui.components.CalmCard
import com.dhruv.focusguard.ui.components.CalmLabel
import com.dhruv.focusguard.ui.components.CalmText
import com.dhruv.focusguard.ui.components.MindfulField
import com.dhruv.focusguard.ui.components.PermissionList
import com.dhruv.focusguard.ui.components.SafetyHero
import com.dhruv.focusguard.ui.components.TransparencyCard
import com.dhruv.focusguard.ui.components.TrustRow
import com.dhruv.focusguard.ui.components.calmBackground
import com.dhruv.focusguard.ui.components.rememberPermSteps
import com.dhruv.focusguard.ui.components.rememberProtectionState
import com.dhruv.focusguard.ui.components.rememberResumeTick
import com.dhruv.focusguard.ui.theme.Sizes
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.delay

/**
 * Settings, permissions first.
 *
 * The four things that keep FocusGuard alive are the whole page, each one
 * explained in plain words with why it's safe and the exact taps for this
 * phone. Everything else lives under "Developer options".
 */
@Composable
fun SettingsScreen(viewModel: TaskViewModel) {
    val steps = rememberPermSteps()
    var devOpen by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .calmBackground()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = Sizes.bottomBarClearance)
        ) {
            Text("Settings", style = CalmText, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(2.dp))
            Text(
                "Four quick steps keep FocusGuard on your side.",
                style = CalmText, fontSize = 14.sp, color = Calm.Sub
            )

            Spacer(Modifier.height(18.dp))
            SafetyHero(steps)
            Spacer(Modifier.height(12.dp))
            TrustRow()

            Spacer(Modifier.height(24.dp))
            CalmLabel("Setup", Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(10.dp))
            PermissionList(steps)

            Spacer(Modifier.height(22.dp))
            TransparencyCard()

            Spacer(Modifier.height(22.dp))
            DeveloperOptions(viewModel = viewModel, open = devOpen, onToggle = { devOpen = !devOpen })

            Spacer(Modifier.height(20.dp))
            Text(
                "FocusGuard 1.1 · Everything stays on this phone.",
                style = CalmText, fontSize = 12.sp, color = Calm.Faint, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Developer options (everything that isn't one of the four steps)
// ═════════════════════════════════════════════════════════════════

@Composable
private fun DeveloperOptions(viewModel: TaskViewModel, open: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state = rememberProtectionState()
    val resume = rememberResumeTick()
    val store = remember { PrefsGuardStore(context) }
    var timerDuration by remember { mutableIntStateOf(viewModel.timerDuration) }
    var name by remember { mutableStateOf(viewModel.userName) }
    var showUnlock by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "devArrow")

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(resume) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val penalty = remember(now) { GuardEngine(store).currentPenaltySeconds(now) }
    val unlockLeftMs = (store.tamperUnlockedUntil - now).coerceAtLeast(0)

    CalmCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DevIcon(Icons.Rounded.Code)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Developer options", style = CalmText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Pause length, name, auto-repair, maintenance", style = CalmText, fontSize = 12.5.sp, color = Calm.Sub)
            }
            Icon(
                Icons.Rounded.ExpandMore, contentDescription = if (open) "Hide" else "Show", tint = Calm.Faint,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(arrow)
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(200)) + expandVertically(spring(0.85f, 380f)),
            exit = fadeOut(tween(150)) + shrinkVertically(spring(0.9f, 500f))
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Divider()

                // Pause length
                DevRow(Icons.Rounded.Timer, "Pause length", "${timerDuration}s before Instagram unlocks")
                Slider(
                    value = timerDuration.toFloat(),
                    onValueChange = { timerDuration = it.toInt() },
                    onValueChangeFinished = { viewModel.timerDuration = timerDuration },
                    valueRange = 15f..180f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = Calm.Indigo,
                        activeTrackColor = Calm.Violet,
                        inactiveTrackColor = Calm.Line,
                        activeTickColor = Calm.IndigoDeep,
                        inactiveTickColor = Color(0xFFD8D2E3)
                    )
                )
                Divider()

                // Name
                DevRow(Icons.Rounded.Person, "Your name", "Shown on the pause screen")
                Spacer(Modifier.height(6.dp))
                MindfulField(
                    label = "",
                    value = name,
                    onValueChange = {
                        name = it
                        viewModel.userName = it
                    },
                    placeholder = "Your first name"
                )
                Spacer(Modifier.height(10.dp))
                Divider()

                // Guard status
                DevRow(
                    if (state.running) Icons.Rounded.Build else Icons.Rounded.Warning,
                    if (state.running) "Guard is running" else "Guard is off",
                    if (state.running) "Accessibility service is bound" else "Finish step 2 above"
                )
                Divider()

                // Auto-repair
                DevRow(
                    Icons.Rounded.Refresh,
                    "Auto-repair",
                    if (state.canSelfHeal) "On. FocusGuard switches itself back on if it's killed"
                    else "Off. Tap to copy the one-time adb command",
                    onClick = if (state.canSelfHeal) null else ({
                        clipboard.setText(AnnotatedString(Protection.ADB_GRANT_COMMAND))
                        Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                    })
                )
                Divider()

                // Penalty
                DevRow(
                    Icons.Rounded.Warning,
                    "Skip penalty",
                    if (penalty > 0) "+${penalty}s on your next pause. Sit through it once and it's gone"
                    else "None. Leaving a pause early adds +5s to the next one (max +60s)"
                )
                Divider()

                // Maintenance unlock
                DevRow(
                    Icons.Rounded.LockOpen,
                    if (unlockLeftMs > 0) "Settings unlocked" else "Unlock FocusGuard's settings",
                    if (unlockLeftMs > 0) "Update, uninstall or switch off FocusGuard for ${formatMs(unlockLeftMs)}"
                    else "Needed to update, uninstall or switch off FocusGuard. Takes a 60-second wait.",
                    onClick = if (unlockLeftMs > 0) null else ({ showUnlock = true })
                )
            }
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

@Composable
private fun DevIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFFEFEDF6)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Calm.Indigo, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DevRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DevIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = CalmText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = CalmText, fontSize = 12.5.sp, color = Calm.Sub, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Calm.Line)
    )
}

/**
 * A deliberate 60-second wait before the tamper lock lifts. Leaving the app
 * resets it.
 */
@Composable
private fun UnlockDialog(onDismiss: () -> Unit, onUnlocked: () -> Unit) {
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
        containerColor = Color.White,
        title = { Text("Unlock FocusGuard's settings?", style = CalmText, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (secondsLeft > 0) {
                    "Wait ${secondsLeft}s. Leaving this screen restarts the wait. " +
                        "Once unlocked you have 5 minutes to update, uninstall or change FocusGuard."
                } else {
                    "Unlocked for 5 minutes once you confirm."
                },
                style = CalmText, color = Calm.Sub
            )
        },
        confirmButton = {
            TextButton(onClick = onUnlocked, enabled = secondsLeft == 0) {
                Text("Unlock", style = CalmText, color = if (secondsLeft == 0) Calm.Indigo else Calm.Faint, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = CalmText, color = Calm.Sub) }
        }
    )
}

private const val UNLOCK_WAIT_SECONDS = 60

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
