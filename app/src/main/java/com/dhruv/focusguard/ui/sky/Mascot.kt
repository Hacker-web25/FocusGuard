package com.dhruv.focusguard.ui.sky

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.dhruv.focusguard.guard.Haptics
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The bubble on the rock at the top of the To-do screen. Everything is drawn
 * as a fraction of the box it is given, so it fits any phone.
 *
 * [mood] is how much of the list is done (0..1): it looks up, curious, at an
 * untouched list, smiles as you go and beams when it is all done. Bump
 * [celebrate] when a task is ticked and it hops and throws hearts. Tap it for
 * a giggle, rub it and it glows.
 */
@Composable
fun Mascot(
    mood: Float,
    celebrate: Int,
    haptics: Haptics?,
    modifier: Modifier = Modifier,
    reference: Boolean = false
) {
    val pose = remember { BubblePose() }
    val frame = remember { mutableLongStateOf(0L) }
    val s = remember { MascotState() }
    val hearts = remember { ArrayList<FloatArray>() }   // x, y, vx, vy, born

    // Body, as fractions of the box
    val cx = 0.50f; val cy = 0.455f; val r = 0.285f

    fun burst(n: Int) {
        repeat(n) { i ->
            val a = (-90f + (i - n / 2f) * 22f) * (Math.PI / 180f).toFloat()
            hearts += floatArrayOf(cx, cy - r * 0.8f, cos(a) * 0.22f, sin(a) * 0.30f - 0.05f, s.time)
        }
    }

    LaunchedEffect(celebrate) {
        if (celebrate > 0) {
            s.liftV = -2.6f; s.sqV = -4f
            s.reactUntil = s.time + 1.6f
            s.glow = 1f
            burst(7)
            haptics?.tick()
        }
    }

    LaunchedEffect(Unit) {
        if (!SkyMotion.enabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                s.time += dt
                s.liftV += (-s.lift * 140f - s.liftV * 8f) * dt; s.lift += s.liftV * dt
                s.sqV += (-s.sq * 240f - s.sqV * 10f) * dt; s.sq += s.sqV * dt
                s.glow = (s.glow - dt * 0.5f).coerceAtLeast(0f)
                if (s.time > s.nextBlink) { s.blinkAt = s.time; s.nextBlink = s.time + 2.5f + Random.nextFloat() * 3f }
                hearts.removeAll { s.time - it[4] > 1.4f }
                for (h in hearts) { h[0] += h[2] * dt; h[1] += h[3] * dt; h[3] += 0.14f * dt }
                frame.longValue = now
            }
        }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    haptics?.click()
                    s.liftV = -2.2f; s.sqV = -3f
                    s.reactUntil = s.time + 1.3f
                    burst(4)
                    var travelled = 0f
                    var last = down.position
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        travelled += (ch.position - last).getDistance()
                        last = ch.position
                        if (travelled > 40f) {
                            travelled = 0f
                            s.glow = (s.glow + 0.25f).coerceAtMost(1f)
                            haptics?.tick()
                        }
                        ch.consume()
                    }
                }
            }
    ) {
        frame.longValue
        val w = size.width
        val h = size.height
        fun px(fx: Float, fy: Float) = Offset(fx * w, fy * h)
        val rr = r * w

        drawRock(w, h)

        val drift = sin(s.time * 0.9f)
        petal(px(0.10f, 0.44f + drift * 0.012f), w * 0.045f, -35f + drift * 8f, 0.85f)
        petal(px(0.93f, 0.60f - drift * 0.010f), w * 0.036f, 45f - drift * 10f, 0.8f)

        val done = mood.coerceIn(0f, 1f)
        val face = when {
            reference -> Face(Eyes.Happy, Mouth.Smile)
            s.time < s.reactUntil -> Face(Eyes.Happy, Mouth.Laugh)
            done >= 1f -> Face(Eyes.Happy, Mouth.Laugh)
            done > 0f -> Face(Eyes.Happy, Mouth.Smile)
            else -> Face(Eyes.Open, Mouth.Smile)
        }
        val bt = (s.time - s.blinkAt) / 0.16f
        pose.eyeOpen = if (bt in 0f..1f) abs(bt * 2f - 1f) else 1f
        pose.lift = s.lift + sin(s.time * 1.6f) * 0.02f
        pose.squashX = 1f + s.sq * 0.45f
        pose.squashY = 1f - s.sq * 0.6f
        pose.leafSway = sin(s.time * 1.2f) * 0.6f
        pose.glow = s.glow
        pose.look = Offset(-0.3f, 0.2f)

        drawJelly(
            c = px(cx, cy), rx = rr * 1.06f, ry = rr,
            face = face, pose = pose,
            layout = FaceLayout(eyeDx = 0.31f, eyeDy = -0.07f, eyeSize = 1.08f, mouthDy = 0.16f, mouthSize = 1.32f, blushDx = 0.50f, blushDy = 0.16f),
            sprouts = Sprouts.Stems,
            time = s.time,
            brightness = 0.55f + 0.4f * done,
            twinkles = 10
        )

        if (done >= 1f) {
            for (i in 0 until 4) {
                val a = s.time * 0.8f + i * 1.57f
                val tw = 0.5f + 0.5f * sin(s.time * 3f + i)
                sparkle(px(cx + cos(a) * r * 1.25f, cy + sin(a) * r * 1.35f), w * 0.030f * (0.6f + tw * 0.5f), Color(0xFFFFF3C2).copy(alpha = 0.9f))
            }
        }
        for (hh in hearts) {
            val age = (s.time - hh[4]) / 1.4f
            heartShape(px(hh[0], hh[1]), w * 0.038f * (0.7f + age * 0.5f), Color(0xFFF07AAE).copy(alpha = (1f - age).coerceIn(0f, 1f)))
        }
    }
}

private class MascotState {
    var time = 0f
    var lift = 0f; var liftV = 0f
    var sq = 0f; var sqV = 0f
    var glow = 0f
    var reactUntil = -1f
    var nextBlink = 1.5f
    var blinkAt = -10f
}

/** The mossy rock it sits on. */
private fun DrawScope.drawRock(w: Float, h: Float) {
    fun px(fx: Float, fy: Float) = Offset(fx * w, fy * h)
    val top = 0.775f
    val bottom = 0.90f
    val outline = listOf(
        0.07f to bottom, 0.085f to 0.80f, 0.12f to 0.782f, 0.22f to 0.776f,
        0.50f to 0.772f, 0.78f to 0.776f, 0.88f to 0.782f, 0.915f to 0.80f, 0.93f to bottom
    )
    val body = Path().apply {
        val f = outline.first(); moveTo(px(f.first, f.second).x, px(f.first, f.second).y)
        for (i in 1 until outline.size) {
            val p = outline[i - 1]; val c = outline[i]
            val mid = px((p.first + c.first) / 2f, (p.second + c.second) / 2f)
            quadraticBezierTo(px(p.first, p.second).x, px(p.first, p.second).y, mid.x, mid.y)
        }
        val l = outline.last(); lineTo(px(l.first, l.second).x, px(l.first, l.second).y)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFFA493C4), Color(0xFF7A7499), Color(0xFF5A5878)), startY = top * h, endY = bottom * h))
    drawPath(
        Path().apply {
            moveTo(px(0.10f, 0.795f).x, px(0.10f, 0.795f).y)
            quadraticBezierTo(px(0.14f, 0.779f).x, px(0.14f, 0.779f).y, px(0.24f, 0.776f).x, px(0.24f, 0.776f).y)
            lineTo(px(0.78f, 0.776f).x, px(0.78f, 0.776f).y)
            quadraticBezierTo(px(0.87f, 0.780f).x, px(0.87f, 0.780f).y, px(0.905f, 0.796f).x, px(0.905f, 0.796f).y)
        },
        Color(0xFFF1D9E6).copy(alpha = 0.9f), style = Stroke(width = w * 0.012f, cap = StrokeCap.Round)
    )
    val crack = Color(0xFF5E5A86).copy(alpha = 0.55f)
    for ((a, b) in listOf(
        (0.18f to 0.80f) to (0.24f to 0.84f),
        (0.44f to 0.79f) to (0.49f to 0.82f),
        (0.72f to 0.80f) to (0.80f to 0.845f)
    )) {
        drawLine(crack, px(a.first, a.second), px(b.first, b.second), strokeWidth = w * 0.006f, cap = StrokeCap.Round)
    }
    // Moss cushions with a few blades
    for ((mcx, mw) in listOf(0.145f to 0.16f, 0.855f to 0.18f)) {
        drawOval(
            Brush.verticalGradient(listOf(Color(0xFF7DA592), Color(0xFF4E7266)), startY = 0.763f * h, endY = 0.790f * h),
            topLeft = px(mcx - mw / 2, 0.766f), size = Size(mw * w, h * 0.024f), alpha = 0.9f
        )
        for (k in 0 until 5) {
            val bx = mcx - mw * 0.34f + k * mw * 0.17f
            val bh = 0.024f + (k % 3) * 0.013f
            val lean = if (k % 2 == 0) -0.008f else 0.008f
            val blade = Path().apply {
                moveTo(px(bx - 0.005f, 0.772f).x, px(bx - 0.005f, 0.772f).y)
                quadraticBezierTo(
                    px(bx + lean * 0.3f, 0.772f - bh * 0.6f).x, px(bx + lean * 0.3f, 0.772f - bh * 0.6f).y,
                    px(bx + lean, 0.772f - bh).x, px(bx + lean, 0.772f - bh).y
                )
                quadraticBezierTo(
                    px(bx + lean * 0.3f + 0.003f, 0.772f - bh * 0.5f).x, px(bx + lean * 0.3f + 0.003f, 0.772f - bh * 0.5f).y,
                    px(bx + 0.005f, 0.772f).x, px(bx + 0.005f, 0.772f).y
                )
                close()
            }
            drawPath(blade, Color(0xFF5E8C7C))
        }
    }
}
