package com.dhruv.focusguard.guard.overlay

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════════
//  Shared bits
// ═════════════════════════════════════════════════════════════════

@Composable
private fun ModeColumn(content: @Composable ColumnScope.() -> Unit) {
    val top = rememberStatusBarHeight()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = top + 10.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun ModeLabel(text: String) {
    Text(
        text, style = OnSky, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

/** Indigo pill, same family as the Continue button. */
@Composable
fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(0.45f, 600f), label = "pp")
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .shadow(18.dp, RoundedCornerShape(50), ambientColor = P.CtaGlow, spotColor = P.CtaGlow)
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(P.CtaTop, P.CtaMid, P.CtaBottom)))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = BaseText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Frosted pill for secondary actions. */
@Composable
fun GhostPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(0.45f, 600f), label = "gp")
    Glass(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(50),
        tint = Color.White.copy(alpha = 0.55f),
        elevation = 6.dp
    ) {
        Text(
            text, style = BaseText, color = P.Navy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/** Thin ring showing how far through a timed mode you are. */
@Composable
private fun ThinRing(progress: Float, diameter: Dp, color: Color = Color.White) {
    val p by animateFloatAsState(progress, tween(300, easing = LinearEasing), label = "thin")
    Canvas(Modifier.size(diameter)) {
        val sw = 4.dp.toPx()
        val inset = sw / 2
        val arc = Size(this.size.width - sw, this.size.height - sw)
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
        drawArc(color, -90f, 360f * p, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Elapsed fraction of a fixed-length mode, ticking 10×/s. */
@Composable
private fun rememberModeProgress(totalMs: Long): Float {
    val start = remember { SystemClock.elapsedRealtime() }
    var f by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (f < 1f) {
            f = ((SystemClock.elapsedRealtime() - start).toFloat() / totalMs).coerceIn(0f, 1f)
            delay(100)
        }
    }
    return f
}

// ═════════════════════════════════════════════════════════════════
//  Calm Orb: a big version of the bubble to touch and rub for a minute
// ═════════════════════════════════════════════════════════════════

@Composable
fun CalmOrbMode(name: String, haptics: Haptics, onWalkAway: () -> Unit, onBack: () -> Unit) {
    val progress = rememberModeProgress(60_000L)
    val done = progress >= 1f
    val prompts = listOf(
        "Rub the glow slowly.",
        "Notice your shoulders. Let them drop.",
        "Slow breath in… and out.",
        "Nothing to catch up on right now.",
        "Feel lighter, ${name.ifBlank { "friend" }}?"
    )
    val idx = (progress * prompts.size).toInt().coerceIn(0, prompts.lastIndex)

    ModeColumn {
        ModeLabel(if (done) "ONE MINUTE, UNWOUND" else "CALM ORB")
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            ThinRing(progress, 300.dp)
            GlowBubble(
                diameter = 168.dp,
                haptics = haptics,
                progress = { progress },
                finished = done,
                lookAtTimer = false
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            prompts[idx], style = OnSky, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        if (done) {
            PrimaryPill("Close Instagram", onWalkAway)
            Spacer(Modifier.height(10.dp))
        }
        GhostPill(if (done) "Back" else "Stop", onBack)
    }
}

// ═════════════════════════════════════════════════════════════════
//  Breathe: box breathing guided by the bubble (4 in, 4 hold, 4 out, 4 hold)
// ═════════════════════════════════════════════════════════════════

@Composable
fun BreatheMode(haptics: Haptics, onWalkAway: () -> Unit, onBack: () -> Unit) {
    val scale = remember { Animatable(0.72f) }
    var phase by remember { mutableStateOf("Get comfortable") }
    var cycle by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(800)
        repeat(4) { c ->
            cycle = c + 1
            phase = "Breathe in"
            haptics.breath()
            scale.animateTo(1.12f, tween(4000, easing = FastOutSlowInEasing))
            phase = "Hold"
            haptics.tick()
            delay(4000)
            phase = "Breathe out"
            haptics.breath()
            scale.animateTo(0.72f, tween(4000, easing = FastOutSlowInEasing))
            phase = "Hold"
            haptics.tick()
            delay(4000)
        }
        done = true
        phase = "Done"
        haptics.success()
    }

    ModeColumn {
        ModeLabel(if (done) "ONE MINUTE, NO SCROLL" else if (cycle == 0) "BOX BREATHING" else "CYCLE $cycle OF 4")
        Spacer(Modifier.weight(1f))
        GlowBubble(
            diameter = 170.dp,
            haptics = haptics,
            breath = { scale.value },
            finished = done,
            lookAtTimer = false
        )
        Spacer(Modifier.height(10.dp))
        Text(phase, style = OnSky, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (done) "That was better for you than a reel." else "Follow the bubble. In through the nose.",
            style = OnSky, fontSize = 15.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        if (done) {
            PrimaryPill("Close Instagram", onWalkAway)
            Spacer(Modifier.height(10.dp))
        }
        GhostPill(if (done) "Back" else "Stop", onBack)
    }
}

// ═════════════════════════════════════════════════════════════════
//  Tiny Win: a 5-minute sprint on the most urgent task
// ═════════════════════════════════════════════════════════════════

private const val SPRINT_MS = 5 * 60_000L

@Composable
fun TinyWinMode(
    task: Task?,
    haptics: Haptics,
    onComplete: (Task, Int) -> Unit,
    onSprintOver: (Int) -> Unit,
    onBack: () -> Unit
) {
    val start = remember { SystemClock.elapsedRealtime() }
    var left by remember { mutableLongStateOf(SPRINT_MS) }
    LaunchedEffect(start) {
        while (true) {
            left = (SPRINT_MS - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0L)
            if (left == 0L) {
                haptics.success()
                break
            }
            delay(250)
        }
    }
    val over = left == 0L
    val minutes = (((SPRINT_MS - left) + 30_000) / 60_000).toInt().coerceAtLeast(1)

    ModeColumn {
        ModeLabel(if (over) "SPRINT COMPLETE" else "TINY WIN · 5 MIN")
        Spacer(Modifier.height(18.dp))
        Glass(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            tint = P.GlassWarm.copy(alpha = 0.86f)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "ONE SMALL STEP ON", style = BaseText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp, color = P.GoalLabel
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    task?.title ?: "the thing you keep putting off",
                    style = BaseText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(18.dp))
                Box(contentAlignment = Alignment.Center) {
                    ThinRing(1f - left.toFloat() / SPRINT_MS, 200.dp, P.CtaGlow)
                    Text(clockLabel(left), style = Tabular, fontSize = 52.sp, fontWeight = FontWeight.Light, color = P.Navy)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (over) "Five minutes in. The hardest part is behind you."
                    else "Phone down. Just start. Five minutes is enough to break the wall.",
                    style = BaseText, fontSize = 14.sp, color = P.Sub, textAlign = TextAlign.Center, lineHeight = 19.sp
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (task != null) {
            PrimaryPill(if (over) "Mark it done" else "It’s done · mark complete", { haptics.success(); onComplete(task, minutes) })
            Spacer(Modifier.height(10.dp))
        }
        if (over) GhostPill("Close Instagram, keep going", { onSprintOver(minutes) })
        else GhostPill("Back", onBack)
    }
}

// ═════════════════════════════════════════════════════════════════
//  Just Be: a minute of nothing at all
// ═════════════════════════════════════════════════════════════════

@Composable
fun JustBeMode(haptics: Haptics, onWalkAway: () -> Unit, onBack: () -> Unit) {
    val progress = rememberModeProgress(60_000L)
    val done = progress >= 1f
    LaunchedEffect(done) { if (done) haptics.success() }

    ModeColumn {
        ModeLabel(if (done) "THAT WAS YOURS" else "JUST BE")
        Spacer(Modifier.weight(1f))
        Text("Just be.", style = OnSky.copy(fontFamily = Hand), fontSize = 46.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            if (done) "A whole minute that nobody sold you anything."
            else "No tasks. No feed. Look out a window, stretch, or do nothing at all.",
            style = OnSky, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp
        )
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .fillMaxWidth(0.6f)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0.01f, 1f))
                    .height(4.dp)
                    .background(Color.White)
            )
        }
        Spacer(Modifier.weight(1f))
        if (done) {
            PrimaryPill("Close Instagram", onWalkAway)
            Spacer(Modifier.height(10.dp))
        }
        GhostPill(if (done) "Back" else "Stop", onBack)
    }
}

// ═════════════════════════════════════════════════════════════════
//  Celebrate: the bubble jumps for joy, then we go home
// ═════════════════════════════════════════════════════════════════

@Composable
fun CelebrateMode(title: String, body: String, streak: Int, haptics: Haptics, onDone: () -> Unit) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(0.4f, 260f)) }
        delay(2300)
        onDone()
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            GlowBubble(diameter = 150.dp, haptics = haptics, progress = { 1f }, finished = true, lookAtTimer = false)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = OnSky, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = OnSky, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
        if (streak > 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                "🔥  Day $streak streak",
                style = BaseText, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
