package com.dhruv.focusguard.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import com.dhruv.focusguard.guard.Haptics
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// The mascot's box on the To-do screen, in mockup pixels.
const val MASCOT_X = 560
const val MASCOT_Y = 80
const val MASCOT_W = 370
const val MASCOT_H = 316

/**
 * The bubble on the rock at the top of the To-do screen.
 *
 * [mood] is how much of today's list is done (0..1): it looks up curious at
 * an empty day, smiles as you go, and beams with sparkles when it's all done.
 * Bump [celebrate] when a task is ticked off and it hops and throws hearts.
 * Tap it for a giggle; rub it and it glows.
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
    var time by remember { mutableStateOf(0f) }
    val hearts = remember { ArrayList<FloatArray>() } // x, y, vx, vy, born
    var lift by remember { mutableStateOf(0f) }
    var liftV by remember { mutableStateOf(0f) }
    var sq by remember { mutableStateOf(0f) }
    var sqV by remember { mutableStateOf(0f) }
    var reactUntil by remember { mutableStateOf(-1f) }
    var glow by remember { mutableStateOf(0f) }
    var nextBlink by remember { mutableStateOf(1.5f) }
    var blinkAt by remember { mutableStateOf(-10f) }

    // Body in mockup px (from the mockup)
    val cx = 756f; val cy = 273f; val rx = 101f; val ry = 88f

    fun burst(n: Int) {
        repeat(n) { i ->
            val a = (-90f + (i - n / 2f) * 22f) * (Math.PI / 180f).toFloat()
            hearts += floatArrayOf(cx, cy - ry * 0.8f, cos(a) * 80f, sin(a) * 110f - 20f, time)
        }
    }

    LaunchedEffect(celebrate) {
        if (celebrate > 0) {
            liftV = -2.6f; sqV = -4f
            reactUntil = time + 1.6f
            glow = 1f
            burst(7)
        }
    }

    LaunchedEffect(Unit) {
        if (!SkyMotion.enabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                time += dt
                liftV += (-lift * 140f - liftV * 8f) * dt; lift += liftV * dt
                sqV += (-sq * 240f - sqV * 10f) * dt; sq += sqV * dt
                glow = (glow - dt * 0.5f).coerceAtLeast(0f)
                if (time > nextBlink) { blinkAt = time; nextBlink = time + 2.5f + Random.nextFloat() * 3f }
                hearts.removeAll { time - it[4] > 1.4f }
                for (h in hearts) { h[0] += h[2] * dt; h[1] += h[3] * dt; h[3] += 50f * dt }
                frame.longValue = now
            }
        }
    }

    Canvas(
        modifier
            .size(MASCOT_W.mp, MASCOT_H.mp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    haptics?.click()
                    liftV = -2.2f; sqV = -3f
                    reactUntil = time + 1.3f
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
                            glow = (glow + 0.25f).coerceAtMost(1f)
                            haptics?.tick()
                        }
                        ch.consume()
                    }
                }
            }
    ) {
        frame.longValue
        val ox = MASCOT_X; val oy = MASCOT_Y
        fun p(x: Float, y: Float) = Offset(mpx(x - ox), mpx(y - oy))

        drawRock(ox, oy)

        // Petals drifting nearby
        val drift = sin(time * 0.9f)
        petal(p(612f, 272f + drift * 4f), mpx(17f), -35f + drift * 8f, 0.85f)
        petal(p(866f, 327f - drift * 3f), mpx(13f), 45f - drift * 10f, 0.8f)

        val done = mood.coerceIn(0f, 1f)
        val face = when {
            reference -> Face(Eyes.Happy, Mouth.Smile)
            time < reactUntil -> Face(Eyes.Happy, Mouth.Laugh)
            done >= 1f -> Face(Eyes.Happy, Mouth.Laugh)
            done > 0f -> Face(Eyes.Happy, Mouth.Smile)
            else -> Face(Eyes.Open, Mouth.Smile)
        }
        val bt = (time - blinkAt) / 0.16f
        pose.eyeOpen = if (bt in 0f..1f) abs(bt * 2f - 1f) else 1f
        pose.lift = lift + sin(time * 1.6f) * 0.02f
        pose.squashX = 1f + sq * 0.45f
        pose.squashY = 1f - sq * 0.6f
        pose.leafSway = sin(time * 1.2f) * 0.6f
        pose.glow = glow
        pose.look = Offset(-0.3f, 0.2f)

        drawJelly(
            c = p(cx, cy), rx = mpx(rx), ry = mpx(ry),
            face = face, pose = pose,
            layout = FaceLayout(eyeDx = 0.31f, eyeDy = -0.07f, eyeSize = 1.08f, mouthDy = 0.16f, mouthSize = 1.32f, blushDx = 0.50f, blushDy = 0.16f),
            sprouts = Sprouts.Stems,
            warmth = 0f,
            time = time,
            brightness = 0.35f + 0.4f * done
        )

        if (done >= 1f) {
            for (i in 0 until 4) {
                val a = time * 0.8f + i * 1.57f
                val tw = 0.5f + 0.5f * sin(time * 3f + i)
                sparkle(p(cx + cos(a) * rx * 1.25f, cy + sin(a) * ry * 1.1f), mpx(9f) * (0.6f + tw * 0.5f), Color(0xFFFFF3C2).copy(alpha = 0.9f))
            }
        }
        for (h in hearts) {
            val age = (time - h[4]) / 1.4f
            heartShape(p(h[0], h[1]), mpx(12f) * (0.7f + age * 0.5f), Color(0xFFF07AAE).copy(alpha = (1f - age).coerceIn(0f, 1f)))
        }
    }
}

/** The mossy rock the mascot sits on. */
private fun DrawScope.drawRock(ox: Int, oy: Int) {
    fun pt(x: Float, y: Float) = Offset(mpx(x - ox), mpx(y - oy))
    val outline = listOf(
        585f to 396f, 589f to 380f, 598f to 369f, 616f to 362f, 648f to 360f, 700f to 361f,
        760f to 359f, 820f to 360f, 858f to 358f, 887f to 362f, 901f to 370f, 908f to 382f, 911f to 396f
    )
    val body = Path().apply {
        val (x0, y0) = outline.first(); moveTo(pt(x0, y0).x, pt(x0, y0).y)
        for (i in 1 until outline.size) {
            val (px, py) = outline[i - 1]; val (x, y) = outline[i]
            val mid = pt((px + x) / 2f, (py + y) / 2f)
            quadraticBezierTo(pt(px, py).x, pt(px, py).y, mid.x, mid.y)
        }
        val (xl, yl) = outline.last(); lineTo(pt(xl, yl).x, pt(xl, yl).y)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFFB9A6D8), Color(0xFF8E85B2), Color(0xFF6E6A96)), startY = pt(0f, 358f).y, endY = pt(0f, 396f).y))
    // Warm light on the top surface
    drawPath(
        Path().apply {
            moveTo(pt(600f, 368f).x, pt(600f, 368f).y)
            quadraticBezierTo(pt(616f, 361f).x, pt(616f, 361f).y, pt(650f, 361f).x, pt(650f, 361f).y)
            lineTo(pt(858f, 359f).x, pt(858f, 359f).y)
            quadraticBezierTo(pt(888f, 362f).x, pt(888f, 362f).y, pt(899f, 370f).x, pt(899f, 370f).y)
        },
        Color(0xFFF1D9E6).copy(alpha = 0.9f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = mpx(4f), cap = StrokeCap.Round)
    )
    // Cracks and texture
    val crack = Color(0xFF5E5A86).copy(alpha = 0.55f)
    for ((a, b) in listOf((628f to 372f) to (646f to 384f), (700f to 370f) to (716f to 378f), (812f to 371f) to (838f to 382f), (870f to 375f) to (884f to 389f))) {
        drawLine(crack, pt(a.first, a.second), pt(b.first, b.second), strokeWidth = mpx(2f), cap = StrokeCap.Round)
    }
    // Moss cushions with a few blades of grass
    for ((cx, w) in listOf(624f to 56f, 868f to 64f)) {
        drawOval(
            Brush.verticalGradient(listOf(Color(0xFF9BCBA9), Color(0xFF5F927F)), startY = pt(0f, 352f).y, endY = pt(0f, 366f).y),
            topLeft = pt(cx - w / 2, 353f), size = Size(mpx(w), mpx(13f))
        )
        for (k in 0 until 5) {
            val bx = cx - w * 0.35f + k * w * 0.17f
            val h = 9f + (k % 3) * 5f
            val lean = if (k % 2 == 0) -3f else 3f
            val blade = Path().apply {
                moveTo(pt(bx - 2f, 357f).x, pt(bx - 2f, 357f).y)
                quadraticBezierTo(pt(bx + lean * 0.3f, 357f - h * 0.6f).x, pt(bx + lean * 0.3f, 357f - h * 0.6f).y, pt(bx + lean, 357f - h).x, pt(bx + lean, 357f - h).y)
                quadraticBezierTo(pt(bx + lean * 0.3f + 1f, 357f - h * 0.5f).x, pt(bx + lean * 0.3f + 1f, 357f - h * 0.5f).y, pt(bx + 2f, 357f).x, pt(bx + 2f, 357f).y)
                close()
            }
            drawPath(blade, Color(0xFF6FA08E))
        }
    }
}
