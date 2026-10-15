package com.dhruv.focusguard.guard.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.guard.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A sparkle or heart that pops out of the bubble and fades. */
private class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val born: Long, val heart: Boolean, val hue: Color)

/**
 * The glowing jelly bubble.
 *
 *  Time:
 *   - Its colour warms from cool blue to pink as the pause runs ([progress]).
 *   - Its eyes watch the timer (to its left) while counting down.
 *   - In the last five seconds it bounces on every tick, eyes wide.
 *   - At zero it jumps and closes its eyes happily.
 *  Touch:
 *   - Tap: squish and wobble, giggle tick, a little burst of hearts.
 *   - Rub (drag across it): leans toward your finger like jelly, glows
 *     brighter the more you rub, throws sparkles, and ticks under your finger.
 *     Rub it to full glow and it gets happy (success haptic).
 *
 * @param breath optional external scale (used by the Breathe mode).
 */
@Composable
fun GlowBubble(
    diameter: Dp,
    haptics: Haptics,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 0f },
    secondsLeft: () -> Int = { Int.MAX_VALUE },
    finished: Boolean = false,
    breath: () -> Float = { 1f },
    lookAtTimer: Boolean = true,
    onRubbedFull: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    val jump = remember { Animatable(0f) }
    val lean = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val glow = remember { Animatable(0f) }
    val eyeOpen = remember { Animatable(1f) }
    var happy by remember { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    val sparks = remember { mutableStateListOf<Spark>() }
    // Written every frame, read only inside the Canvas below: the bubble
    // repaints without the composable recomposing.
    val frame = remember { mutableLongStateOf(0L) }
    var rubbedFull by remember { mutableStateOf(false) }

    // Idle life: bob, wing flap, iridescent spin
    val t = rememberInfiniteTransition(label = "bubble")
    val bobS = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val flapS = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flap")
    val spinS = t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")

    // Frame clock for sparks + glow decay
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { ms ->
                frame.longValue = ms
                sparks.removeAll { ms - it.born > 1100 }
            }
            if (!touching && glow.value > 0f) glow.snapTo((glow.value - 0.004f).coerceAtLeast(0f))
        }
    }

    // Blink every few seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(2200L + Random.nextLong(2800))
            eyeOpen.animateTo(0.08f, tween(70))
            eyeOpen.animateTo(1f, tween(120))
        }
    }

    // Bounce on each of the last five seconds. Derived, so this composable
    // wakes once a second rather than on every countdown update.
    val seconds by remember(secondsLeft) { derivedStateOf { secondsLeft() } }
    LaunchedEffect(seconds) {
        if (seconds in 1..5 && !finished) {
            launch { sy.animateTo(0.86f, tween(90)); sy.animateTo(1f, spring(0.3f, 420f)) }
            launch { sx.animateTo(1.12f, tween(90)); sx.animateTo(1f, spring(0.3f, 420f)) }
            jump.animateTo(-0.10f, tween(140, easing = FastOutSlowInEasing))
            jump.animateTo(0f, spring(0.45f, 380f))
        }
    }

    // Celebrate at zero
    LaunchedEffect(finished) {
        if (finished) {
            happy = true
            burst(sparks, 14, frame.longValue, heart = true)
            jump.animateTo(-0.35f, tween(260, easing = FastOutSlowInEasing))
            jump.animateTo(0f, spring(0.35f, 300f))
        }
    }

    fun tapReaction() {
        haptics.click()
        happy = true
        burst(sparks, 6, frame.longValue, heart = true)
        scope.launch { sy.animateTo(0.80f, tween(80)); sy.animateTo(1f, spring(0.25f, 380f)) }
        scope.launch { sx.animateTo(1.16f, tween(80)); sx.animateTo(1f, spring(0.25f, 380f)) }
        scope.launch {
            delay(900)
            if (!finished && !rubbedFull) happy = false
        }
    }

    Canvas(
        modifier
            .size(diameter * 1.7f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touching = true
                    finger = down.position
                    haptics.tick()
                    scope.launch { sy.animateTo(0.92f, spring(0.5f, 600f)) }
                    scope.launch { sx.animateTo(1.06f, spring(0.5f, 600f)) }
                    var travelled = 0f
                    var rubAcc = 0f
                    var last = down.position
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = min(size.width, size.height) / 1.7f / 2f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val d = (ch.position - last).getDistance()
                        travelled += d
                        rubAcc += d
                        last = ch.position
                        finger = ch.position
                        // Jelly lean toward the finger, capped
                        val toward = ch.position - c
                        val dist = toward.getDistance().coerceAtLeast(1f)
                        val lim = r * 0.22f
                        val k = min(dist, r) / r * lim / dist
                        scope.launch { lean.animateTo(Offset(toward.x * k, toward.y * k), spring(0.6f, 900f)) }
                        if (rubAcc > r * 0.35f) {
                            rubAcc = 0f
                            haptics.tick()
                            sparks.add(
                                Spark(
                                    ch.position.x - c.x, ch.position.y - c.y,
                                    Random.nextFloat() * 2f - 1f, -Random.nextFloat() * 1.5f - 0.5f,
                                    frame.longValue, heart = false, hue = if (Random.nextBoolean()) Color.White else P.TimerPink
                                )
                            )
                            scope.launch {
                                glow.snapTo((glow.value + 0.07f).coerceAtMost(1f))
                                if (glow.value >= 0.99f && !rubbedFull) {
                                    rubbedFull = true
                                    happy = true
                                    haptics.success()
                                    burst(sparks, 12, frame.longValue, heart = true)
                                    onRubbedFull?.invoke()
                                }
                            }
                        }
                        ch.consume()
                    }
                    touching = false
                    scope.launch { lean.animateTo(Offset.Zero, spring(0.28f, 260f)) }
                    scope.launch { sy.animateTo(1.06f, tween(80)); sy.animateTo(1f, spring(0.28f, 300f)) }
                    scope.launch { sx.animateTo(0.95f, tween(80)); sx.animateTo(1f, spring(0.28f, 300f)) }
                    if (travelled < 14.dp.toPx()) tapReaction()
                }
            }
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 1.7f / 2f * breath()
        val now = frame.longValue
        val bobY = (bobS.value - 0.5f) * r * 0.08f + jump.value * r
        val warm = progress().coerceIn(0f, 1f)
        val col1 = lerp(P.BubbleCool1, P.BubbleWarm1, warm)
        val col2 = lerp(P.BubbleCool2, P.BubbleWarm2, warm)
        val glowA = 0.30f + 0.55f * glow.value + if (finished) 0.2f else 0f

        // Halo
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = glowA), col2.copy(alpha = glowA * 0.5f), Color.Transparent),
                center = c + Offset(0f, bobY), radius = r * 1.55f
            ),
            radius = r * 1.55f, center = c + Offset(0f, bobY)
        )

        translate(lean.value.x * 0.35f, bobY + lean.value.y * 0.35f) {
            // Wings / petals behind the body
            val flapDeg = (flapS.value - 0.5f) * 14f + if (touching) 8f else 0f
            wing(c, r, left = true, deg = -flapDeg, tint = col2)
            wing(c, r, left = false, deg = flapDeg, tint = col2)

            // Feet
            drawOval(Color.White.copy(alpha = 0.55f), topLeft = Offset(c.x - r * 0.42f, c.y + r * 0.86f), size = Size(r * 0.28f, r * 0.18f))
            drawOval(Color.White.copy(alpha = 0.55f), topLeft = Offset(c.x + r * 0.14f, c.y + r * 0.86f), size = Size(r * 0.28f, r * 0.18f))

            scale(sx.value, sy.value, pivot = Offset(c.x, c.y + r)) {
                // Body
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.97f),
                        0.45f to lerp(Color.White, col2, 0.45f),
                        0.82f to col1,
                        1f to lerp(col1, P.TimerPink, 0.5f),
                        center = c + Offset(-r * 0.25f, -r * 0.30f), radius = r * 1.25f
                    ),
                    radius = r, center = c
                )
                // Iridescent film
                rotate(spinS.value, pivot = c) {
                    drawCircle(
                        Brush.sweepGradient(
                            listOf(
                                Color(0x55FF9ED8), Color(0x339FC1FE), Color(0x44B6F0E0),
                                Color(0x33CBB8FF), Color(0x55FF9ED8)
                            ),
                            center = c
                        ),
                        radius = r, center = c
                    )
                }
                // Inner bounce light
                drawOval(
                    Brush.radialGradient(listOf(P.TimerPink.copy(alpha = 0.55f), Color.Transparent), center = Offset(c.x, c.y + r * 0.62f), radius = r * 0.6f),
                    topLeft = Offset(c.x - r * 0.6f, c.y + r * 0.25f), size = Size(r * 1.2f, r * 0.7f)
                )
                // Rim
                drawCircle(Color.White.copy(alpha = 0.85f), radius = r, center = c, style = Stroke(r * 0.035f))
                drawCircle(Color.White.copy(alpha = 0.25f), radius = r * 0.93f, center = c, style = Stroke(r * 0.02f))
                // Specular highlight
                rotate(-32f, pivot = Offset(c.x - r * 0.42f, c.y - r * 0.5f)) {
                    drawOval(
                        Color.White.copy(alpha = 0.75f),
                        topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.62f),
                        size = Size(r * 0.42f, r * 0.22f)
                    )
                }
                drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.05f, center = Offset(c.x + r * 0.45f, c.y - r * 0.48f))

                // Face
                val look = when {
                    touching -> {
                        val d = finger - c
                        val m = d.getDistance().coerceAtLeast(1f)
                        Offset(d.x / m, d.y / m) * (r * 0.05f)
                    }
                    finished || !lookAtTimer -> Offset.Zero
                    else -> Offset(-r * 0.05f, -r * 0.01f)
                }
                face(
                    c = c + lean.value * 0.25f,
                    r = r,
                    look = look,
                    eyeOpen = eyeOpen.value,
                    happy = happy || finished,
                    excited = (seconds in 1..5 && !finished) || glow.value > 0.6f
                )
            }
        }

        // Sparks
        for (s in sparks) {
            val age = ((now - s.born).coerceAtLeast(0L)) / 1100f
            val px = c.x + s.x + s.vx * r * 0.9f * age
            val py = c.y + s.y + s.vy * r * 0.9f * age
            val a = (1f - age).coerceIn(0f, 1f)
            val sz = r * 0.10f * (0.6f + age * 0.6f)
            if (s.heart) heart(Offset(px, py), sz, P.Blush.copy(alpha = a))
            else star(Offset(px, py), sz, s.hue.copy(alpha = a))
        }
    }
}

private fun burst(sparks: MutableList<Spark>, n: Int, now: Long, heart: Boolean) {
    repeat(n) { i ->
        val a = (i / n.toFloat()) * 6.283f + Random.nextFloat() * 0.4f
        sparks.add(
            Spark(
                cos(a) * 60f, sin(a) * 60f - 20f,
                cos(a) * 1.4f, sin(a) * 1.4f - 0.6f,
                now, heart = heart && i % 2 == 0,
                hue = if (i % 3 == 0) P.TimerPink else Color.White
            )
        )
    }
}

private fun DrawScope.wing(c: Offset, r: Float, left: Boolean, deg: Float, tint: Color) {
    val dir = if (left) -1f else 1f
    val base = Offset(c.x + dir * r * 0.78f, c.y - r * 0.25f)
    rotate(deg, pivot = base) {
        val tip = Offset(base.x + dir * r * 0.55f, base.y - r * 0.55f)
        val p = Path().apply {
            moveTo(base.x, base.y)
            quadraticBezierTo(base.x + dir * r * 0.05f, tip.y - r * 0.05f, tip.x, tip.y)
            quadraticBezierTo(tip.x + dir * r * 0.05f, base.y + r * 0.1f, base.x, base.y)
            close()
        }
        drawPath(p, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), tint.copy(alpha = 0.55f)), start = base, end = tip))
        drawPath(p, Color.White.copy(alpha = 0.7f), style = Stroke(r * 0.015f))
    }
}

private fun DrawScope.face(c: Offset, r: Float, look: Offset, eyeOpen: Float, happy: Boolean, excited: Boolean) {
    val eyeY = c.y - r * 0.02f
    val dx = r * 0.24f
    val ink = Color(0xFF26214F)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(c.x + side * dx, eyeY) + look
        if (happy) {
            val p = Path().apply {
                moveTo(e.x - r * 0.08f, e.y + r * 0.02f)
                quadraticBezierTo(e.x, e.y - r * 0.09f, e.x + r * 0.08f, e.y + r * 0.02f)
            }
            drawPath(p, ink, style = Stroke(r * 0.04f, cap = StrokeCap.Round))
        } else {
            val ew = r * (if (excited) 0.13f else 0.115f)
            val eh = r * (if (excited) 0.17f else 0.15f) * eyeOpen.coerceAtLeast(0.08f)
            drawOval(ink, topLeft = Offset(e.x - ew / 2, e.y - eh / 2), size = Size(ew, eh))
            if (eyeOpen > 0.5f) {
                drawCircle(Color.White, radius = r * 0.032f, center = Offset(e.x + ew * 0.18f, e.y - eh * 0.2f))
                drawCircle(Color.White.copy(alpha = 0.7f), radius = r * 0.015f, center = Offset(e.x - ew * 0.15f, e.y + eh * 0.2f))
            }
        }
        // Blush
        drawOval(
            P.Blush.copy(alpha = 0.55f),
            topLeft = Offset(c.x + side * r * 0.40f - r * 0.09f, c.y + r * 0.12f),
            size = Size(r * 0.18f, r * 0.10f)
        )
    }
    // Mouth
    val m = Offset(c.x, c.y + r * 0.17f) + look * 0.5f
    if (excited || happy) {
        val p = Path().apply {
            moveTo(m.x - r * 0.08f, m.y - r * 0.01f)
            quadraticBezierTo(m.x, m.y + r * 0.14f, m.x + r * 0.08f, m.y - r * 0.01f)
            close()
        }
        drawPath(p, Color(0xFFB0365F))
    } else {
        val p = Path().apply {
            moveTo(m.x - r * 0.07f, m.y)
            quadraticBezierTo(m.x, m.y + r * 0.08f, m.x + r * 0.07f, m.y)
        }
        drawPath(p, ink, style = Stroke(r * 0.03f, cap = StrokeCap.Round))
    }
}

fun DrawScope.star(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - s)
        quadraticBezierTo(c.x, c.y, c.x + s, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y + s)
        quadraticBezierTo(c.x, c.y, c.x - s, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y - s)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.heart(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + s * 0.9f)
        cubicTo(c.x - s * 1.4f, c.y - s * 0.1f, c.x - s * 0.6f, c.y - s * 1.1f, c.x, c.y - s * 0.35f)
        cubicTo(c.x + s * 0.6f, c.y - s * 1.1f, c.x + s * 1.4f, c.y - s * 0.1f, c.x, c.y + s * 0.9f)
        close()
    }
    drawPath(p, color)
}

@Suppress("unused")
private fun angleOf(v: Offset) = atan2(v.y, v.x)
