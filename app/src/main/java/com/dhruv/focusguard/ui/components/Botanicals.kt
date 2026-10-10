package com.dhruv.focusguard.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.dhruv.focusguard.ui.theme.mindful

/**
 * Botanical art, drawn with Compose paths and gradients.
 *
 * Nothing here loads a bitmap or touches the network, so illustrations never
 * fail to load, never shift layout, and scale cleanly to any density.
 */

/** A single leaf, drawn as two mirrored quadratic curves. */
private fun DrawScope.leaf(
    origin: Offset,
    length: Float,
    width: Float,
    angleRad: Float,
    color: Color,
    veinColor: Color? = null
) {
    val tipX = origin.x + length * kotlin.math.cos(angleRad)
    val tipY = origin.y + length * kotlin.math.sin(angleRad)

    // Perpendicular offset for the belly of the leaf
    val perpX = -kotlin.math.sin(angleRad) * width
    val perpY = kotlin.math.cos(angleRad) * width
    val midX = (origin.x + tipX) / 2f
    val midY = (origin.y + tipY) / 2f

    val path = Path().apply {
        moveTo(origin.x, origin.y)
        quadraticBezierTo(midX + perpX, midY + perpY, tipX, tipY)
        quadraticBezierTo(midX - perpX, midY - perpY, origin.x, origin.y)
        close()
    }
    drawPath(path, color)

    veinColor?.let {
        drawLine(
            color = it,
            start = origin,
            end = Offset(tipX, tipY),
            strokeWidth = size.minDimension * 0.006f
        )
    }
}

/** A small five-petal bloom. */
private fun DrawScope.bloom(center: Offset, radius: Float, petal: Color, heart: Color) {
    repeat(5) { i ->
        val a = (i * 72f) * (Math.PI / 180f).toFloat() - (Math.PI / 2f).toFloat()
        leaf(
            origin = center,
            length = radius,
            width = radius * 0.34f,
            angleRad = a,
            color = petal
        )
    }
    drawCircle(heart, radius * 0.26f, center)
}

/**
 * Header illustration for the Tasks screen: soft layered hills with a
 * growing sprig. Intentionally low-contrast so it never competes with
 * the task information sitting above it.
 */
@Composable
fun BotanicalHeader(modifier: Modifier = Modifier) {
    val c = MaterialTheme.mindful
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Far hill
        val far = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.62f)
            quadraticBezierTo(w * 0.26f, h * 0.30f, w * 0.52f, h * 0.60f)
            quadraticBezierTo(w * 0.74f, h * 0.82f, w, h * 0.54f)
            lineTo(w, h)
            close()
        }
        drawPath(far, c.powderBlue.copy(alpha = if (c.isDark) 0.22f else 0.45f))

        // Near hill
        val near = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.80f)
            quadraticBezierTo(w * 0.32f, h * 0.56f, w * 0.60f, h * 0.80f)
            quadraticBezierTo(w * 0.82f, h * 0.96f, w, h * 0.78f)
            lineTo(w, h)
            close()
        }
        drawPath(near, c.accentSoft.copy(alpha = if (c.isDark) 0.55f else 1f))

        // Sprig rising from the near hill
        val stemBase = Offset(w * 0.78f, h * 0.84f)
        val stemTop = Offset(w * 0.80f, h * 0.34f)
        drawPath(
            Path().apply {
                moveTo(stemBase.x, stemBase.y)
                quadraticBezierTo(w * 0.73f, h * 0.58f, stemTop.x, stemTop.y)
            },
            color = c.accentDeep.copy(alpha = 0.75f),
            style = Stroke(width = w * 0.007f)
        )
        leaf(Offset(w * 0.765f, h * 0.60f), w * 0.085f, w * 0.030f, -0.45f, c.accent.copy(alpha = 0.95f))
        leaf(Offset(w * 0.755f, h * 0.49f), w * 0.075f, w * 0.026f, 3.5f, c.accentDeep.copy(alpha = 0.60f))
        bloom(stemTop, w * 0.042f, c.blush.copy(alpha = 0.95f), c.peach)

        // Drifting pollen
        drawCircle(c.lavender.copy(alpha = 0.50f), w * 0.009f, Offset(w * 0.90f, h * 0.26f))
        drawCircle(c.accent.copy(alpha = 0.40f), w * 0.006f, Offset(w * 0.68f, h * 0.22f))
        drawCircle(c.peach.copy(alpha = 0.60f), w * 0.007f, Offset(w * 0.88f, h * 0.46f))
    }
}

/**
 * The luminous orb at the centre of the pause overlay.
 *
 * [breathing] drives a slow expansion and contraction. When reduced motion is
 * requested the caller passes `false` and the orb simply rests, fully legible.
 */
@Composable
fun BotanicalOrb(
    modifier: Modifier = Modifier,
    breathing: Boolean = true
) {
    val c = MaterialTheme.mindful
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )
    val scale = if (breathing) pulse else 1f

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val mid = center

            // Outer halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        c.lavender.copy(alpha = 0.26f),
                        c.lavender.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = mid,
                    radius = r * scale
                ),
                radius = r * scale,
                center = mid
            )

            // Glass body
            val bodyR = r * 0.62f * scale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f),
                        c.powderBlue.copy(alpha = 0.26f),
                        c.lavender.copy(alpha = 0.34f)
                    ),
                    center = Offset(mid.x - bodyR * 0.3f, mid.y - bodyR * 0.35f),
                    radius = bodyR * 1.5f
                ),
                radius = bodyR,
                center = mid
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = bodyR,
                center = mid,
                style = Stroke(width = r * 0.012f)
            )

            // Highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.26f),
                radius = bodyR * 0.16f,
                center = Offset(mid.x - bodyR * 0.40f, mid.y - bodyR * 0.46f)
            )

            // Bloom inside the glass
            val stemBase = Offset(mid.x, mid.y + bodyR * 0.46f)
            drawPath(
                Path().apply {
                    moveTo(stemBase.x, stemBase.y)
                    quadraticBezierTo(mid.x - bodyR * 0.12f, mid.y, mid.x, mid.y - bodyR * 0.10f)
                },
                color = c.accent.copy(alpha = 0.85f),
                style = Stroke(width = r * 0.016f)
            )
            leaf(Offset(mid.x - bodyR * 0.03f, mid.y + bodyR * 0.16f), bodyR * 0.36f, bodyR * 0.13f, -2.55f, c.accent.copy(alpha = 0.80f))
            leaf(Offset(mid.x + bodyR * 0.02f, mid.y + bodyR * 0.06f), bodyR * 0.32f, bodyR * 0.11f, -0.62f, c.accentDeep.copy(alpha = 0.85f))
            bloom(
                center = Offset(mid.x, mid.y - bodyR * 0.22f),
                radius = bodyR * 0.30f,
                petal = c.blush.copy(alpha = 0.92f),
                heart = c.peach
            )

            // Base glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.accent.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(mid.x, mid.y + bodyR * 0.95f),
                    radius = bodyR * 0.85f
                ),
                radius = bodyR * 0.85f,
                center = Offset(mid.x, mid.y + bodyR * 0.95f)
            )
        }
    }
}

/**
 * Quiet night sky behind the pause overlay: a vertical wash plus a few
 * still points of light. No flashing, no restless particles.
 */
@Composable
fun NightSky(modifier: Modifier = Modifier) {
    val c = MaterialTheme.mindful
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(c.focusCanvasDeep, c.focusCanvas, c.focusSurface),
                startY = 0f,
                endY = size.height
            ),
            size = Size(size.width, size.height)
        )
        // Soft lift behind the orb area
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c.lavender.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.30f),
                radius = size.width * 0.72f
            ),
            radius = size.width * 0.72f,
            center = Offset(size.width * 0.5f, size.height * 0.30f)
        )
        val stars = listOf(
            Triple(0.14f, 0.10f, 0.42f), Triple(0.82f, 0.08f, 0.30f),
            Triple(0.28f, 0.19f, 0.22f), Triple(0.68f, 0.22f, 0.36f),
            Triple(0.90f, 0.33f, 0.20f), Triple(0.08f, 0.30f, 0.26f)
        )
        stars.forEach { (fx, fy, a) ->
            drawCircle(
                color = Color.White.copy(alpha = a),
                radius = size.width * 0.0045f,
                center = Offset(size.width * fx, size.height * fy)
            )
        }
    }
}

/** Small sprig used in empty states and the completion moment. */
@Composable
fun SprigMark(modifier: Modifier = Modifier, tint: Color? = null) {
    val c = MaterialTheme.mindful
    val stem = tint ?: c.accentDeep
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawPath(
            Path().apply {
                moveTo(w * 0.50f, h * 0.95f)
                quadraticBezierTo(w * 0.40f, h * 0.55f, w * 0.52f, h * 0.22f)
            },
            color = stem.copy(alpha = 0.80f),
            style = Stroke(width = w * 0.045f)
        )
        leaf(Offset(w * 0.47f, h * 0.68f), w * 0.30f, w * 0.11f, -0.35f, c.accent.copy(alpha = 0.90f))
        leaf(Offset(w * 0.45f, h * 0.50f), w * 0.26f, w * 0.095f, 3.55f, c.accentSoft)
        bloom(Offset(w * 0.52f, h * 0.20f), w * 0.17f, c.blush, c.peach)
    }
}
