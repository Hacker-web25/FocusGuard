package com.dhruv.focusguard.ui.components

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.guard.Oem
import com.dhruv.focusguard.guard.PrefsGuardStore
import com.dhruv.focusguard.guard.Protection
import com.dhruv.focusguard.guard.overlay.Inter
import kotlinx.coroutines.delay

// ═════════════════════════════════════════════════════════════════
//  Look: calm lavender → peach, white cards, Inter. Same family as the
//  pause screen so the app feels like one thing.
// ═════════════════════════════════════════════════════════════════

object Calm {
    val Ink = Color(0xFF1C1A3A)
    val Sub = Color(0xFF6B6383)
    val Faint = Color(0xFF9A93AE)
    val Card = Color.White
    val Line = Color(0xFFEDE7F2)
    val Indigo = Color(0xFF4F4CA6)
    val IndigoDeep = Color(0xFF3B3A8C)
    val Violet = Color(0xFF8B6CF6)
    val Green = Color(0xFF2E9E7E)
    val GreenSoft = Color(0xFFDDF4EA)
    val Amber = Color(0xFFB7791F)
    val AmberSoft = Color(0xFFFFF0D6)
    val Grey = Color(0xFF8C879C)
    val GreySoft = Color(0xFFEFEDF3)
    val Shadow = Color(0xFF5A4A8A)
}

val CalmText = TextStyle(fontFamily = Inter, color = Calm.Ink)

/** Soft page background for the in-app screens. */
fun Modifier.calmBackground(): Modifier = this.background(
    Brush.verticalGradient(
        0f to Color(0xFFE9E6FB),
        0.45f to Color(0xFFF6EEF4),
        1f to Color(0xFFFCEFE6)
    )
)

// ═════════════════════════════════════════════════════════════════
//  Model
// ═════════════════════════════════════════════════════════════════

enum class StepStatus { Done, Needed, NotNeeded, Unknown }

class PermStep(
    val key: String,
    val title: String,
    val why: String,
    val safety: String,
    val icon: ImageVector,
    val tint: Color,
    val tile: Color,
    val status: StepStatus,
    val steps: List<String>,
    val action: String,
    val onAction: () -> Unit,
    val confirmable: Boolean = false,
    val secondary: String? = null,
    val onSecondary: (() -> Unit)? = null
)

/** Build the four setup steps with live status for this phone. */
@Composable
fun rememberPermSteps(): List<PermStep> {
    val context = LocalContext.current
    val store = remember { PrefsGuardStore(context) }
    val resume = rememberResumeTick()
    var poll by remember { mutableIntStateOf(0) }
    LaunchedEffect(resume) {
        while (true) {
            delay(1500)
            poll++
        }
    }
    var autostartConfirmed by remember { mutableStateOf(store.autostartConfirmed) }

    // Re-read the phone's real state on resume and every 1.5s.
    val live = remember(poll, resume) {
        Triple(
            Protection.isRunning(context),
            Oem.restrictedSettingsAllowed(context),
            Protection.isIgnoringBatteryOptimizations(context)
        )
    }
    val running = live.first
    val restricted = live.second
    val battery = live.third
    val brand = Oem.brand

    return listOf(
        PermStep(
            key = "restricted",
            title = "Allow restricted settings",
            why = "Android blocks apps installed from a file from using accessibility until you say it’s okay.",
            safety = "This only unlocks the next step for FocusGuard. It doesn’t give FocusGuard anything by itself.",
            icon = Icons.Rounded.PhonelinkLock,
            tint = Color(0xFF7A5AF8),
            tile = Color(0xFFEDE7FF),
            status = when {
                !Oem.needsRestrictedSettings -> StepStatus.NotNeeded
                restricted == true -> StepStatus.Done
                restricted == false -> StepStatus.Needed
                else -> StepStatus.Unknown
            },
            steps = Oem.restrictedSteps(),
            action = "Open App info",
            onAction = { Oem.openRestrictedSettings(context) },
            secondary = "Open Pause screen settings",
            onSecondary = { Oem.openAccessibility(context) }
        ),
        PermStep(
            key = "accessibility",
            title = "Pause screen",
            why = "Lets FocusGuard notice when Instagram opens and show your pause on top of it.",
            safety = "Android warns that this kind of app could read your screen. FocusGuard only checks which app is open, and reads text only on its own settings pages so it can’t be switched off by accident. It never reads your messages, photos or feed.",
            icon = Icons.Rounded.Shield,
            tint = Color(0xFF3E8FC4),
            tile = Color(0xFFDDEEFA),
            status = if (running) StepStatus.Done else StepStatus.Needed,
            steps = Oem.accessibilitySteps(),
            action = "Open Pause screen settings",
            onAction = { Oem.openAccessibility(context) }
        ),
        PermStep(
            key = "battery",
            title = "Battery: no restrictions",
            why = "Stops your phone from quietly putting FocusGuard to sleep, which is when Instagram would slip through.",
            safety = "FocusGuard does nothing until an app opens, so this doesn’t drain your battery. You can switch it back any time.",
            icon = Icons.Rounded.BatteryChargingFull,
            tint = Color(0xFF2E9E7E),
            tile = Color(0xFFDDF4EA),
            status = if (battery) StepStatus.Done else StepStatus.Needed,
            steps = Oem.batterySteps(),
            action = "Open battery settings",
            onAction = {
                // These screens live next to Force stop; give a short window so the
                // tamper lock doesn't bounce the user out mid-setup.
                if (!battery) store.tamperUnlockedUntil = System.currentTimeMillis() + 90_000L
                Oem.openBattery(context, preferBrandScreen = battery)
            },
            secondary = if (battery && brand in listOf(Oem.Brand.XIAOMI, Oem.Brand.SAMSUNG, Oem.Brand.HUAWEI, Oem.Brand.HONOR)) "Extra ${brand.label} step" else null,
            onSecondary = { Oem.openBattery(context, preferBrandScreen = true) }
        ),
        PermStep(
            key = "autostart",
            title = "Autostart",
            why = "Lets FocusGuard start again after your phone restarts, so you’re never unprotected.",
            safety = "FocusGuard starts silently in the background. No notifications, nothing on screen until Instagram opens.",
            icon = Icons.Rounded.RocketLaunch,
            tint = Color(0xFFE08A1E),
            tile = Color(0xFFFFEFD9),
            status = when {
                !brand.hasAutostart -> StepStatus.NotNeeded
                autostartConfirmed -> StepStatus.Done
                else -> StepStatus.Unknown
            },
            steps = Oem.autostartSteps(),
            action = "Open ${brand.label} autostart",
            onAction = { Oem.openAutostart(context) },
            confirmable = brand.hasAutostart && !autostartConfirmed,
            onSecondary = {
                store.autostartConfirmed = true
                autostartConfirmed = true
            }
        )
    )
}

fun List<PermStep>.doneCount() = count { it.status == StepStatus.Done || it.status == StepStatus.NotNeeded }

// ═════════════════════════════════════════════════════════════════
//  Hero: shield + progress
// ═════════════════════════════════════════════════════════════════

@Composable
fun SafetyHero(steps: List<PermStep>, modifier: Modifier = Modifier) {
    val done = steps.doneCount()
    val total = steps.size
    val allDone = done == total
    val p by animateFloatAsState(done / total.toFloat(), tween(700, easing = FastOutSlowInEasing), label = "hero")

    Box(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = Calm.Shadow.copy(alpha = 0.3f), spotColor = Calm.Shadow.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    if (allDone) listOf(Color(0xFF2E9E7E), Color(0xFF4FB79A), Color(0xFF7FCFB3))
                    else listOf(Calm.IndigoDeep, Calm.Indigo, Calm.Violet)
                )
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 6.dp.toPx()
                    val inset = sw / 2
                    val arc = Size(size.width - sw, size.height - sw)
                    drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
                    drawArc(Color.White, -90f, 360f * p, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
                }
                ShieldGlyph(allDone, Modifier.size(38.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (allDone) "You’re fully protected" else "${total - done} step${if (total - done == 1) "" else "s"} to full protection",
                    style = CalmText, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (allDone) "Instagram is paused every time it opens, even after a restart."
                    else "Each step stops your phone from switching FocusGuard off behind your back.",
                    style = CalmText, color = Color.White.copy(alpha = 0.88f), fontSize = 13.5.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$done of $total done",
                    style = CalmText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ShieldGlyph(done: Boolean, modifier: Modifier) {
    val s by animateFloatAsState(if (done) 1f else 0.9f, spring(0.4f, 300f), label = "shield")
    Canvas(modifier.graphicsLayer { scaleX = s; scaleY = s }) {
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w * 0.92f, h * 0.16f)
            cubicTo(w * 0.92f, h * 0.62f, w * 0.72f, h * 0.86f, w / 2f, h)
            cubicTo(w * 0.28f, h * 0.86f, w * 0.08f, h * 0.62f, w * 0.08f, h * 0.16f)
            close()
        }
        drawPath(shield, Color.White)
        val ink = if (done) Color(0xFF2E9E7E) else Calm.Indigo
        if (done) {
            val check = Path().apply {
                moveTo(w * 0.30f, h * 0.50f)
                lineTo(w * 0.45f, h * 0.65f)
                lineTo(w * 0.72f, h * 0.36f)
            }
            drawPath(check, ink, style = Stroke(w * 0.11f, cap = StrokeCap.Round))
        } else {
            // keyhole
            drawCircle(ink, w * 0.11f, Offset(w / 2f, h * 0.42f))
            drawLine(ink, Offset(w / 2f, h * 0.45f), Offset(w / 2f, h * 0.68f), w * 0.10f, StrokeCap.Round)
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Trust pills
// ═════════════════════════════════════════════════════════════════

@Composable
fun TrustRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrustPill(Icons.Rounded.WifiOff, "No internet\naccess", Modifier.weight(1f))
        TrustPill(Icons.Rounded.VisibilityOff, "No ads or\ntracking", Modifier.weight(1f))
        TrustPill(Icons.Rounded.Replay, "Undo any\nstep anytime", Modifier.weight(1f))
    }
}

@Composable
private fun TrustPill(icon: ImageVector, text: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.75f))
            .border(1.dp, Color.White, RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Calm.GreenSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Calm.Green, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(text, style = CalmText, fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

// ═════════════════════════════════════════════════════════════════
//  The step cards
// ═════════════════════════════════════════════════════════════════

@Composable
fun PermissionList(steps: List<PermStep>, modifier: Modifier = Modifier) {
    val view = LocalView.current
    // Celebrate (haptic) when a step flips to done while you're looking
    val last = remember { mutableStateMapOf<String, StepStatus>() }
    LaunchedEffect(steps.map { it.status }) {
        for (s in steps) {
            val prev = last[s.key]
            if (prev != null && prev != StepStatus.Done && s.status == StepStatus.Done) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.LONG_PRESS
                )
            }
            last[s.key] = s.status
        }
    }
    // The first step that still needs doing starts open.
    val firstOpen = steps.firstOrNull { it.status == StepStatus.Needed || it.status == StepStatus.Unknown }?.key
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEachIndexed { i, step ->
            PermissionCard(index = i + 1, step = step, startOpen = step.key == firstOpen)
        }
    }
}

@Composable
private fun PermissionCard(index: Int, step: PermStep, startOpen: Boolean) {
    var open by remember(step.key, startOpen) { mutableStateOf(startOpen) }
    val done = step.status == StepStatus.Done || step.status == StepStatus.NotNeeded
    val arrow by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "arrow")

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = Calm.Shadow.copy(alpha = 0.18f), spotColor = Calm.Shadow.copy(alpha = 0.22f))
            .clip(RoundedCornerShape(24.dp))
            .background(Calm.Card)
            .animateContentSize(spring(0.85f, 380f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (done) Calm.GreenSoft else step.tile),
                contentAlignment = Alignment.Center
            ) {
                if (done) Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Calm.Green, modifier = Modifier.size(24.dp))
                else Icon(step.icon, contentDescription = null, tint = step.tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("STEP $index", style = CalmText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Calm.Faint)
                Text(step.title, style = CalmText, fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
            }
            StatusChip(step.status)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.ExpandMore, contentDescription = if (open) "Hide details" else "Show details", tint = Calm.Faint,
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
                Text(step.why, style = CalmText, fontSize = 14.sp, lineHeight = 20.sp, color = Calm.Sub)
                Spacer(Modifier.height(12.dp))

                // Why it's safe
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Calm.GreenSoft.copy(alpha = 0.7f))
                        .padding(12.dp)
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Calm.Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Why it’s safe", style = CalmText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Calm.Green)
                        Spacer(Modifier.height(2.dp))
                        Text(step.safety, style = CalmText, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF2F5E52))
                    }
                }

                if (!done && step.steps.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "On your ${Oem.brand.label}".uppercase(),
                        style = CalmText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Calm.Faint
                    )
                    Spacer(Modifier.height(8.dp))
                    step.steps.forEachIndexed { i, line ->
                        Row(Modifier.padding(bottom = 8.dp)) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(step.tile),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", style = CalmText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = step.tint)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = CalmText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 1.dp))
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (step.status == StepStatus.NotNeeded) {
                    Text(
                        "Your phone doesn’t need this. Nothing to do.",
                        style = CalmText, fontSize = 13.5.sp, color = Calm.Green, fontWeight = FontWeight.SemiBold
                    )
                } else {
                    CalmButton(
                        text = if (done) "Open again" else step.action,
                        filled = !done,
                        onClick = step.onAction
                    )
                    if (step.secondary != null && step.onSecondary != null && !step.confirmable) {
                        Spacer(Modifier.height(8.dp))
                        CalmButton(text = step.secondary, filled = false, onClick = step.onSecondary)
                    }
                    if (step.confirmable && step.onSecondary != null) {
                        Spacer(Modifier.height(8.dp))
                        CalmButton(text = "I’ve turned it on", filled = false, onClick = step.onSecondary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Your phone doesn’t let apps check this one, so just tell us once it’s on.",
                            style = CalmText, fontSize = 12.sp, color = Calm.Faint, lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: StepStatus) {
    val (label, bg, ink) = when (status) {
        StepStatus.Done -> Triple("Done", Calm.GreenSoft, Calm.Green)
        StepStatus.NotNeeded -> Triple("Not needed", Calm.GreySoft, Calm.Grey)
        StepStatus.Needed -> Triple("Needed", Calm.AmberSoft, Calm.Amber)
        StepStatus.Unknown -> Triple("To check", Calm.AmberSoft, Calm.Amber)
    }
    Text(
        label, style = CalmText, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
fun CalmButton(text: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(0.5f, 600f), label = "cb")
    val view = LocalView.current
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.5f }
            .clip(RoundedCornerShape(50))
            .then(
                if (filled) Modifier.background(Brush.verticalGradient(listOf(Calm.Indigo, Calm.IndigoDeep)))
                else Modifier
                    .background(Color(0xFFF5F2FA))
                    .border(1.dp, Calm.Line, RoundedCornerShape(50))
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, style = CalmText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (filled) Color.White else Calm.Indigo
        )
    }
}

// ═════════════════════════════════════════════════════════════════
//  What FocusGuard can / can't see
// ═════════════════════════════════════════════════════════════════

@Composable
fun TransparencyCard(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.7f))
            .border(1.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Text("What FocusGuard can see", style = CalmText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        SeeLine(true, "Which app is open (to spot Instagram)")
        SeeLine(true, "Text on FocusGuard’s own settings pages (so it can’t be switched off by accident)")
        SeeLine(true, "Your tasks and goals, stored only on this phone")
        Spacer(Modifier.height(6.dp))
        SeeLine(false, "Your messages, photos, passwords or Instagram feed")
        SeeLine(false, "The internet. FocusGuard has no internet permission, so Android won’t let it send anything anywhere")
        SeeLine(false, "Accounts, ads, analytics or trackers")
    }
}

@Composable
private fun SeeLine(can: Boolean, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (can) Color(0xFFE6E3FA) else Color(0xFFFFE3E6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (can) Icons.Rounded.Visibility else Icons.Rounded.Close,
                contentDescription = if (can) "Can see" else "Cannot see",
                tint = if (can) Calm.Indigo else Color(0xFFD2405A),
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = CalmText, fontSize = 13.5.sp, lineHeight = 19.sp, color = if (can) Calm.Ink else Calm.Sub)
    }
}

/** Section label used on these screens. */
@Composable
fun CalmLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = CalmText, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp, color = Calm.Faint, modifier = modifier
    )
}

/** White rounded container for grouped rows (developer options). */
@Composable
fun CalmCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Calm.Shadow.copy(alpha = 0.15f), spotColor = Calm.Shadow.copy(alpha = 0.18f))
            .clip(RoundedCornerShape(24.dp))
            .background(Calm.Card),
        content = content
    )
}

@Suppress("unused")
private fun Context.noop() = Unit
