package com.dhruv.focusguard.guard.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dhruv.focusguard.guard.Haptics
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════
//  "What brought you here?"
//
//  A sheet that rises over the pause a moment after it opens and asks
//  why you reached for Instagram. Each answer leads somewhere better:
//
//    Long day          → put on music (opens Spotify / YT Music / …)
//    Just bored        → a minute of box breathing
//    Avoiding something→ a 5-minute start on your most urgent task
//    Something specific→ fine: the normal pause, wait out the timer
//
//  Answers are counted per day, so the sheet can quietly show you how
//  often "just bored" has been the reason today.
// ═════════════════════════════════════════════════════════════════

enum class Reason(val key: String, val title: String, val sub: String) {
    LongDay("long_day", "Long day", "Unwind with music"),
    Bored("bored", "Just bored", "Take a deep breath"),
    Avoiding("avoiding", "Avoiding something", "Start it for 5 min"),
    Specific("specific", "Something specific", "I’ll wait it out")
}

enum class IntentStage { Asking, Music, Done }

private class CardLook(val bg: Color, val liquid: Color, val ink: Color, val sub: Color)

private fun lookOf(r: Reason) = when (r) {
    Reason.LongDay -> CardLook(Color(0xFFFBE3EC), Color(0xFFF7A3C4), Color(0xFF5E1F47), Color(0xFF946479))
    Reason.Bored -> CardLook(P.BreatheBg, Color(0xFF7FC4F0), P.BreatheInk, P.BreatheSub)
    Reason.Avoiding -> CardLook(P.TinyBg, Color(0xFFFFB86B), P.TinyInk, P.TinySub)
    Reason.Specific -> CardLook(P.OrbBg, Color(0xFFB98CFF), P.OrbInk, P.OrbSub)
}

/** Music apps we know, best-known first. Only installed ones are shown. */
private val MUSIC_PACKAGES = listOf(
    "com.spotify.music",
    "com.google.android.apps.youtube.music",
    "com.jio.media.jiobeats",          // JioSaavn
    "com.bsbportal.music",             // Wynk
    "com.gaana",
    "com.apple.android.music",
    "com.amazon.mp3",
    "deezer.android.app",
    "com.soundcloud.android",
    "com.miui.player",                 // Xiaomi Music
    "com.sec.android.app.music",       // Samsung Music
    "com.heytap.music",                // Oppo / realme / OnePlus Music
    "com.android.bbkmusic",            // vivo i Music
    "com.huawei.music"
)

private class MusicApp(val pkg: String, val label: String, val icon: ImageBitmap?)

/**
 * The sheet. Drawn on top of the pause home; swallows touches while it is up
 * so the timer screen underneath can't be scrolled or tapped by accident.
 */
@Composable
fun IntentLayer(
    state: PauseState,
    haptics: Haptics,
    onPick: (Reason) -> Unit,
    onMusic: (String?) -> Unit,
    onBreathe: () -> Unit,
    onBack: () -> Unit,
    onWalkAway: () -> Unit
) {
    val shown = state.intentStage != IntentStage.Done
    val enter = remember { Animatable(0f) }
    val density = LocalDensity.current
    val travel = with(density) { 520.dp.toPx() }

    LaunchedEffect(shown) {
        if (shown) {
            if (enter.value == 0f) delay(560)
            haptics.tick()
            enter.animateTo(1f, spring(dampingRatio = 0.80f, stiffness = 210f))
        } else {
            enter.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        }
    }
    if (!shown && enter.value <= 0.001f) return

    Box(Modifier.fillMaxSize()) {
        // Dim the pause behind; eat every touch while the question is up.
        Box(
            Modifier
                .fillMaxSize()
                .background(P.Navy.copy(alpha = 0.40f * enter.value))
                .then(
                    if (shown) Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            do {
                                val ev = awaitPointerEvent()
                                ev.changes.forEach { it.consume() }
                            } while (ev.changes.any { it.pressed })
                        }
                    } else Modifier
                )
        )

        Glass(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 16.dp)
                .graphicsLayer {
                    translationY = (1f - enter.value) * travel
                    alpha = enter.value.coerceIn(0f, 1f)
                },
            shape = RoundedCornerShape(34.dp),
            tint = P.GlassWarm.copy(alpha = 0.94f),
            elevation = 24.dp
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)) {
                // Grabber, for the "sheet" feel
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(P.Navy.copy(alpha = 0.14f))
                )
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = if (state.intentStage == IntentStage.Music) IntentStage.Music else IntentStage.Asking,
                    transitionSpec = {
                        val forward = targetState == IntentStage.Music
                        (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(tween(320)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(220)))
                    },
                    label = "intentPage"
                ) { page ->
                    if (page == IntentStage.Music) {
                        MusicPage(haptics = haptics, onMusic = onMusic, onBreathe = onBreathe, onBack = onBack)
                    } else {
                        AskPage(state = state, haptics = haptics, onPick = onPick, onWalkAway = onWalkAway)
                    }
                }
            }
        }
    }
}

@Composable
private fun AskPage(state: PauseState, haptics: Haptics, onPick: (Reason) -> Unit, onWalkAway: () -> Unit) {
    val t = rememberInfiniteTransition(label = "intentArt")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "phase")

    Column {
        Text(
            if (state.userName.isNotBlank()) "What brought you here, ${state.userName}?" else "What brought you here?",
            style = BaseText, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            todayLine(state.intentCounts) ?: "Be honest. There’s no wrong answer.",
            style = BaseText, fontSize = 13.sp, color = P.Sub, lineHeight = 17.sp
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IntentCard(Reason.LongDay, state.intentCounts, phase, haptics, onPick, Modifier.weight(1f))
            IntentCard(Reason.Bored, state.intentCounts, phase, haptics, onPick, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IntentCard(Reason.Avoiding, state.intentCounts, phase, haptics, onPick, Modifier.weight(1f))
            IntentCard(Reason.Specific, state.intentCounts, phase, haptics, onPick, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(50))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    haptics.click(); onWalkAway()
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Eco, contentDescription = null, tint = P.GoalGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Or just close Instagram", style = BaseText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = P.GoalGreen)
        }
    }
}

/** "Today: bored ×3 · long day ×1", or null before any answers today. */
private fun todayLine(counts: Map<String, Int>): String? {
    val parts = Reason.entries
        .mapNotNull { r -> counts[r.key]?.takeIf { it > 0 }?.let { r to it } }
        .sortedByDescending { it.second }
        .map { (r, n) -> "${r.title.lowercase()} ×$n" }
    return if (parts.isEmpty()) null else "Today so far: " + parts.joinToString(" · ")
}

@Composable
private fun IntentCard(
    reason: Reason,
    counts: Map<String, Int>,
    phase: Float,
    haptics: Haptics,
    onPick: (Reason) -> Unit,
    modifier: Modifier
) {
    val look = lookOf(reason)
    val n = counts[reason.key] ?: 0
    LiquidTile(
        background = look.bg,
        liquid = look.liquid,
        haptics = haptics,
        onClick = { onPick(reason) },
        modifier = modifier.height(156.dp),
        radius = 24.dp
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(18.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                when (reason) {
                    Reason.LongDay -> musicArt(phase)
                    Reason.Bored -> breatheArt(phase)
                    Reason.Avoiding -> summitArt(phase)
                    Reason.Specific -> waitArt(phase)
                }
            }
            if (n > 0) {
                Text(
                    "×$n today",
                    style = BaseText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = look.ink,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.75f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            reason.title, style = BaseText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = look.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(1.dp))
        Text(
            reason.sub, style = BaseText, fontSize = 11.5.sp, color = look.sub,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

// ── Long day → music ────────────────────────────────────────────

@Composable
private fun MusicPage(haptics: Haptics, onMusic: (String?) -> Unit, onBreathe: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        MUSIC_PACKAGES.mapNotNull { pkg ->
            try {
                if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                val info = pm.getApplicationInfo(pkg, 0)
                val icon = try {
                    pm.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                MusicApp(pkg, pm.getApplicationLabel(info).toString(), icon)
            } catch (e: Exception) {
                null
            }
        }.take(3)
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.7f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptics.click(); onBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = P.Navy, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Long day. Let’s unwind.", style = BaseText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Put on something you love instead of scrolling. Instagram closes and your music opens.",
            style = BaseText, fontSize = 13.sp, color = P.Sub, lineHeight = 18.sp
        )
        Spacer(Modifier.height(14.dp))

        val look = lookOf(Reason.LongDay)
        if (apps.isEmpty()) {
            LiquidTile(
                background = look.bg, liquid = look.liquid, haptics = haptics,
                onClick = { onMusic(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                radius = 24.dp
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = look.ink, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(6.dp))
                Text("Open my music player", style = BaseText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = look.ink)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (app in apps) {
                    LiquidTile(
                        background = look.bg, liquid = look.liquid, haptics = haptics,
                        onClick = { onMusic(app.pkg) },
                        modifier = Modifier
                            .weight(1f)
                            .height(112.dp),
                        radius = 24.dp
                    ) {
                        AppIcon(app.icon, 46.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            app.label, style = BaseText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = look.ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GhostPill("Breathe for a minute instead", { haptics.click(); onBreathe() })
    }
}

// ═════════════════════════════════════════════════════════════════
//  Card illustrations (all code-drawn and gently animated)
// ═════════════════════════════════════════════════════════════════

private const val TAU = (2 * PI).toFloat()

/** Dusk, a sunset, headphones, notes floating up. */
private fun DrawScope.musicArt(phase: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFCDB6F6), Color(0xFFF7B5CB), Color(0xFFFFD9B4))))
    val sun = Offset(w * 0.5f, h * 0.86f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xCCFFF3D1), Color(0x00FFF3D1)), center = sun, radius = h * 0.55f),
        radius = h * 0.55f, center = sun
    )
    drawCircle(Color(0xFFFFE6A8), radius = h * 0.22f, center = sun)
    val hills = Path().apply {
        moveTo(0f, h * 0.86f)
        cubicTo(w * 0.25f, h * 0.70f, w * 0.45f, h * 0.92f, w * 0.65f, h * 0.80f)
        cubicTo(w * 0.80f, h * 0.72f, w * 0.92f, h * 0.80f, w, h * 0.76f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(hills, Color(0xFFB08ADB))

    // Headphones
    val c = Offset(w * 0.5f, h * 0.50f)
    val r = h * 0.26f
    drawArc(
        Color.White, startAngle = 190f, sweepAngle = 160f, useCenter = false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2f, r * 2f),
        style = Stroke(width = h * 0.075f, cap = StrokeCap.Round)
    )
    val cupW = h * 0.15f
    val cupH = h * 0.26f
    for (side in intArrayOf(-1, 1)) {
        val x = c.x + side * r - cupW / 2f
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White, Color(0xFFF0E2FF)), startY = c.y - cupH * 0.2f, endY = c.y + cupH),
            topLeft = Offset(x, c.y - cupH * 0.15f), size = Size(cupW, cupH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cupW * 0.45f)
        )
        drawRoundRect(
            Color(0xFF9B7BE0),
            topLeft = Offset(x + (if (side < 0) cupW * 0.62f else cupW * 0.08f), c.y),
            size = Size(cupW * 0.30f, cupH * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cupW * 0.15f)
        )
    }
    // Notes drift up and fade
    for (k in 0 until 3) {
        val p = (phase + k / 3f) % 1f
        val x = w * (0.22f + k * 0.28f) + sin(p * TAU) * w * 0.04f
        val y = h * 0.92f - p * h * 0.85f
        val a = sin(p * PI.toFloat()).coerceIn(0f, 1f)
        note(Offset(x, y), h * 0.07f, Color.White.copy(alpha = a))
    }
}

private fun DrawScope.note(p: Offset, s: Float, color: Color) {
    drawCircle(color, radius = s * 0.55f, center = p)
    drawLine(color, Offset(p.x + s * 0.48f, p.y), Offset(p.x + s * 0.48f, p.y - s * 1.9f), strokeWidth = s * 0.22f, cap = StrokeCap.Round)
    drawLine(color, Offset(p.x + s * 0.48f, p.y - s * 1.9f), Offset(p.x + s * 1.2f, p.y - s * 1.4f), strokeWidth = s * 0.22f, cap = StrokeCap.Round)
}

/** Soft sky, a breathing circle, wind drifting through. */
private fun DrawScope.breatheArt(phase: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFDCEEFF), Color(0xFFA9CBF5))))
    // Little cloud
    val cl = Offset(w * 0.22f, h * 0.26f)
    drawCircle(Color.White.copy(alpha = 0.9f), radius = h * 0.09f, center = cl)
    drawCircle(Color.White.copy(alpha = 0.9f), radius = h * 0.12f, center = Offset(cl.x + h * 0.12f, cl.y - h * 0.03f))
    drawCircle(Color.White.copy(alpha = 0.9f), radius = h * 0.08f, center = Offset(cl.x + h * 0.24f, cl.y + h * 0.01f))

    val breath = 0.5f - 0.5f * kotlin.math.cos(phase * TAU)   // 0 → 1 → 0 over one cycle
    val c = Offset(w * 0.56f, h * 0.54f)
    val r = h * (0.18f + 0.10f * breath)
    drawCircle(Color.White.copy(alpha = 0.35f), radius = r * 1.45f, center = c, style = Stroke(width = h * 0.02f))
    drawCircle(
        Brush.radialGradient(listOf(Color.White, Color(0xFFBFE0FF), Color(0xFF7FB8EE)), center = Offset(c.x - r * 0.3f, c.y - r * 0.3f), radius = r * 1.3f),
        radius = r, center = c
    )
    // Wind lines flowing right
    for (k in 0 until 2) {
        val y0 = h * (0.30f + k * 0.48f)
        val shift = ((phase * 2f + k * 0.5f) % 1f) * w * 0.5f
        val path = Path().apply {
            moveTo(-w * 0.2f + shift, y0)
            cubicTo(w * 0.05f + shift, y0 - h * 0.06f, w * 0.20f + shift, y0 + h * 0.06f, w * 0.38f + shift, y0)
        }
        drawPath(path, Color.White.copy(alpha = 0.75f), style = Stroke(width = h * 0.028f, cap = StrokeCap.Round))
    }
}

/** A mountain with a flag, and a dot climbing the trail. */
private fun DrawScope.summitArt(phase: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFEBC9), Color(0xFFFFC39E))))
    drawCircle(Color(0x66FFFFFF), radius = h * 0.16f, center = Offset(w * 0.18f, h * 0.26f))
    val back = Path().apply {
        moveTo(-w * 0.05f, h); lineTo(w * 0.30f, h * 0.40f); lineTo(w * 0.62f, h); close()
    }
    drawPath(back, Color(0xFFF6A889))
    val peak = Offset(w * 0.62f, h * 0.24f)
    val front = Path().apply {
        moveTo(w * 0.18f, h); lineTo(peak.x, peak.y); lineTo(w * 1.06f, h); close()
    }
    drawPath(front, Brush.verticalGradient(listOf(Color(0xFFF08A68), Color(0xFFDB6A57)), startY = peak.y, endY = h))
    val snow = Path().apply {
        moveTo(peak.x, peak.y)
        lineTo(peak.x - w * 0.075f, peak.y + h * 0.16f)
        lineTo(peak.x - w * 0.02f, peak.y + h * 0.12f)
        lineTo(peak.x + w * 0.02f, peak.y + h * 0.17f)
        lineTo(peak.x + w * 0.07f, peak.y + h * 0.15f)
        close()
    }
    drawPath(snow, Color.White)
    // Flag waving on the summit
    val pole = Offset(peak.x, peak.y - h * 0.20f)
    drawLine(P.Navy.copy(alpha = 0.75f), peak, pole, strokeWidth = h * 0.022f, cap = StrokeCap.Round)
    val wave = sin(phase * TAU * 2f) * h * 0.035f
    val flag = Path().apply {
        moveTo(pole.x, pole.y)
        quadraticBezierTo(pole.x + w * 0.07f, pole.y + wave, pole.x + w * 0.14f, pole.y + h * 0.04f)
        quadraticBezierTo(pole.x + w * 0.07f, pole.y + h * 0.08f + wave, pole.x, pole.y + h * 0.10f)
        close()
    }
    drawPath(flag, Brush.horizontalGradient(listOf(P.CtaGlow, P.LiquidTo), startX = pole.x, endX = pole.x + w * 0.14f))
    // Zig-zag trail with a climber
    val pts = listOf(
        Offset(w * 0.30f, h * 0.98f), Offset(w * 0.56f, h * 0.78f), Offset(w * 0.44f, h * 0.60f),
        Offset(w * 0.60f, h * 0.44f), Offset(peak.x, peak.y + h * 0.05f)
    )
    for (i in 0 until pts.size - 1) {
        val a = pts[i]
        val b = pts[i + 1]
        for (d in 0 until 5) {
            val t = d / 5f
            drawCircle(Color.White.copy(alpha = 0.8f), radius = h * 0.012f, center = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
        }
    }
    val climb = FastOutSlowInEasing.transform((phase * 1.25f).coerceAtMost(1f))
    val f = climb * (pts.size - 1)
    val i = f.toInt().coerceAtMost(pts.size - 2)
    val lt = f - i
    val pos = Offset(pts[i].x + (pts[i + 1].x - pts[i].x) * lt, pts[i].y + (pts[i + 1].y - pts[i].y) * lt)
    drawCircle(Color(0x66FFFFFF), radius = h * 0.06f, center = pos)
    drawCircle(Color.White, radius = h * 0.032f, center = pos)
}

/** A message bubble with typing dots. */
private fun DrawScope.waitArt(phase: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE9E1FF), Color(0xFFC8B6FF))))
    drawCircle(Color(0x55FFFFFF), radius = h * 0.30f, center = Offset(w * 0.85f, h * 0.15f))
    val bw = w * 0.56f
    val bh = h * 0.42f
    val left = (w - bw) / 2f
    val top = h * 0.24f
    val tail = Path().apply {
        moveTo(left + bw * 0.16f, top + bh - 2f)
        lineTo(left + bw * 0.06f, top + bh + h * 0.13f)
        lineTo(left + bw * 0.38f, top + bh - 2f)
        close()
    }
    drawPath(tail, Color.White)
    drawRoundRect(
        Color.White, topLeft = Offset(left, top), size = Size(bw, bh),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(bh * 0.5f)
    )
    for (i in 0 until 3) {
        val p = (phase * 2f - i * 0.14f + 1f) % 1f
        val lift = abs(sin(p * PI.toFloat())) * h * 0.06f
        drawCircle(
            Color(0xFF8E6BFF).copy(alpha = 0.55f + 0.45f * (lift / (h * 0.06f))),
            radius = h * 0.045f,
            center = Offset(left + bw * (0.30f + i * 0.20f), top + bh / 2f - lift)
        )
    }
}
