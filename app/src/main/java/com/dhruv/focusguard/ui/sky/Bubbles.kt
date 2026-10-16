package com.dhruv.focusguard.ui.sky

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════
//  The jelly bubble characters from the mockups: a glossy, glowing
//  sphere, blue at the top melting to pink at the bottom, with a
//  bright rim, little feet and a face that can change.
// ═════════════════════════════════════════════════════════════════

enum class Eyes { Open, Happy, Wink, Wide, Sleepy, Hearts, Stars }

enum class Mouth { Smile, Small, Grin, Laugh, Oh, Wobbly, Flat }

@Immutable
data class Face(val eyes: Eyes, val mouth: Mouth, val blush: Float = 1f)

/** Where the face sits, as fractions of the bubble's radii (characters differ a little). */
@Immutable
data class FaceLayout(
    val eyeDx: Float = 0.30f,
    val eyeDy: Float = -0.16f,
    val eyeSize: Float = 1f,
    val mouthDy: Float = 0.06f,
    val mouthSize: Float = 1f,
    val blushDx: Float = 0.50f,
    val blushDy: Float = 0.08f
)

enum class Sprouts { None, LeafPairs, Stems }

/** Everything that changes frame to frame for one bubble. */
class BubblePose(
    var squashX: Float = 1f,
    var squashY: Float = 1f,
    var lift: Float = 0f,          // fraction of ry, negative = up
    var tilt: Float = 0f,          // degrees
    var eyeOpen: Float = 1f,       // blink, 0..1
    var look: Offset = Offset.Zero, // unit-ish vector the pupils lean toward
    var glow: Float = 0f,          // 0..1 extra brightness
    var leafSway: Float = 0f       // -1..1
)

private val BodyStops = arrayOf(
    0f to Color(0xFF9DB4FB),
    0.42f to Color(0xFFAFB4FD),
    0.72f to Color(0xFFDCC6FB),
    1f to Color(0xFFFBDDF8)
)

/**
 * Draws one bubble centred at [c] with radii [rx]/[ry] (px). [warmth] tints
 * the body toward pink (0..1); [time] drives the twinkle inside.
 */
fun DrawScope.drawJelly(
    c: Offset,
    rx: Float,
    ry: Float,
    face: Face,
    pose: BubblePose,
    layout: FaceLayout = FaceLayout(),
    sprouts: Sprouts = Sprouts.None,
    warmth: Float = 0f,
    time: Float = 0f,
    brightness: Float = 0f,
    feet: Boolean = true
) {
    val cy = c.y + pose.lift * ry
    val center = Offset(c.x, cy)
    val bx = rx * pose.squashX
    val by = ry * pose.squashY
    // Squash keeps the feet on the ground: grow/shrink from the bottom.
    val body = Offset(center.x, center.y + (ry - by))

    // Soft glow around the whole character
    val glowA = 0.42f + 0.35f * pose.glow + 0.25f * brightness
    drawOval(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = glowA),
            0.55f to Color(0xFFF6E6FF).copy(alpha = glowA * 0.45f),
            1f to Color.Transparent,
            center = body, radius = bx * 1.45f
        ),
        topLeft = Offset(body.x - bx * 1.45f, body.y - by * 1.45f),
        size = Size(bx * 2.9f, by * 2.9f)
    )

    rotate(pose.tilt, pivot = Offset(body.x, body.y + by)) {
        if (sprouts != Sprouts.None) drawSprouts(body, bx, by, sprouts, pose.leafSway, time)

        if (feet) {
            for (side in listOf(-1f, 1f)) {
                val fc = Offset(body.x + side * bx * 0.42f, c.y + ry * 0.90f)
                val fw = rx * 0.40f
                val fh = ry * 0.24f
                drawOval(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF4F0FF), Color(0xFFD3C5FC)),
                        startY = fc.y - fh / 2, endY = fc.y + fh / 2
                    ),
                    topLeft = Offset(fc.x - fw / 2, fc.y - fh / 2), size = Size(fw, fh), alpha = 0.92f
                )
                drawOval(
                    Color.White.copy(alpha = 0.7f),
                    topLeft = Offset(fc.x - fw * 0.30f, fc.y - fh * 0.38f), size = Size(fw * 0.32f, fh * 0.28f)
                )
            }
        }

        // Body
        val oval = Rect(body.x - bx, body.y - by, body.x + bx, body.y + by)
        val warm = warmth.coerceIn(0f, 1f)
        drawOval(
            Brush.verticalGradient(
                *BodyStops.map { (p, col) -> p to lerp(col, Color(0xFFF7C9EE), warm * (0.35f + 0.4f * p)) }.toTypedArray(),
                startY = oval.top, endY = oval.bottom
            ),
            topLeft = oval.topLeft, size = oval.size
        )
        val shape = Path().apply { addOval(oval) }
        clipPath(shape) {
            // Lavender on the left, peach-pink on the right, like light through glass
            drawOval(
                Brush.radialGradient(
                    listOf(Color(0xFFC6B6FC).copy(alpha = 0.75f), Color.Transparent),
                    center = Offset(oval.left + bx * 0.15f, body.y + by * 0.1f), radius = bx * 0.9f
                ),
                topLeft = oval.topLeft, size = oval.size
            )
            drawOval(
                Brush.radialGradient(
                    listOf(Color(0xFFFCDDF4).copy(alpha = 0.85f), Color.Transparent),
                    center = Offset(oval.right - bx * 0.1f, body.y + by * 0.15f), radius = bx * 0.85f
                ),
                topLeft = oval.topLeft, size = oval.size
            )
            // Inner light, brighter as the character gets happier
            drawOval(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.30f + 0.35f * brightness + 0.3f * pose.glow), Color.Transparent),
                    center = Offset(body.x, body.y + by * 0.45f), radius = bx * 0.9f
                ),
                topLeft = oval.topLeft, size = oval.size
            )
            // Iridescent film drifting round
            rotate(time * 18f, pivot = body) {
                drawOval(
                    Brush.sweepGradient(
                        listOf(
                            Color(0x33FFB8E6), Color(0x229FC1FE), Color(0x22B6F0E0),
                            Color(0x22CBB8FF), Color(0x33FFB8E6)
                        ),
                        center = body
                    ),
                    topLeft = oval.topLeft, size = oval.size
                )
            }
            // Soft white crescent along the upper-left inside the rim
            drawArc(
                Color.White.copy(alpha = 0.55f),
                startAngle = 150f, sweepAngle = 110f, useCenter = false,
                topLeft = Offset(oval.left + bx * 0.10f, oval.top + by * 0.10f),
                size = Size(bx * 1.8f, by * 1.8f),
                style = Stroke(width = bx * 0.07f, cap = StrokeCap.Round)
            )
            // Twinkles
            for (i in 0 until 9) {
                val a = i * 2.39996f
                val rr = 0.25f + 0.6f * ((i * 0.37f) % 1f)
                val p = Offset(body.x + cos(a) * bx * rr, body.y + sin(a) * by * rr * 0.9f)
                val tw = 0.5f + 0.5f * sin(time * 2.2f + i * 1.7f)
                drawCircle(Color.White.copy(alpha = 0.25f + 0.55f * tw), radius = bx * (0.012f + 0.01f * (i % 3)), center = p)
            }
        }
        // Rim: bright, thick at the bottom-right where the light pools
        drawOval(
            Brush.sweepGradient(
                listOf(
                    Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.75f),
                    Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.95f)
                ),
                center = body
            ),
            topLeft = oval.topLeft, size = oval.size, style = Stroke(width = bx * 0.045f)
        )
        drawOval(
            Color.White.copy(alpha = 0.22f),
            topLeft = Offset(oval.left - bx * 0.04f, oval.top - by * 0.04f),
            size = Size(bx * 2.08f, by * 2.08f), style = Stroke(width = bx * 0.08f)
        )
        // Specular highlights
        rotate(-35f, pivot = Offset(body.x - bx * 0.48f, body.y - by * 0.55f)) {
            drawOval(
                Color.White.copy(alpha = 0.8f),
                topLeft = Offset(body.x - bx * 0.66f, body.y - by * 0.64f),
                size = Size(bx * 0.36f, by * 0.16f)
            )
        }
        drawCircle(Color.White.copy(alpha = 0.95f), radius = bx * 0.035f, center = Offset(body.x + bx * 0.50f, body.y - by * 0.55f))

        drawFace(body, bx, by, face, pose, layout)
    }
}

private fun DrawScope.drawSprouts(body: Offset, bx: Float, by: Float, kind: Sprouts, sway: Float, time: Float) {
    val leafFill = Brush.linearGradient(listOf(Color(0xFFF8FBFF), Color(0xFFC5D3FB), Color(0xFFE3D2FB)))
    fun leaf(base: Offset, angle: Float, len: Float, width: Float) {
        rotate(angle, pivot = base) {
            val tip = Offset(base.x, base.y - len)
            val p = Path().apply {
                moveTo(base.x, base.y)
                cubicTo(base.x - width, base.y - len * 0.25f, base.x - width * 0.9f, base.y - len * 0.8f, tip.x, tip.y)
                cubicTo(base.x + width * 0.9f, base.y - len * 0.8f, base.x + width, base.y - len * 0.25f, base.x, base.y)
                close()
            }
            drawPath(p, Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFBFD0FB), Color(0xFFE4D4FC)), start = base, end = tip), alpha = 0.92f)
            drawPath(p, Color.White.copy(alpha = 0.9f), style = Stroke(width = width * 0.10f))
            drawLine(Color.White.copy(alpha = 0.8f), base, Offset(base.x, base.y - len * 0.85f), strokeWidth = width * 0.08f, cap = StrokeCap.Round)
        }
    }
    val wiggle = sway * 6f + sin(time * 1.6f) * 3f
    when (kind) {
        Sprouts.LeafPairs -> {
            // A two-leaf sprout at the upper left and upper right, plus little rays of light
            for (side in listOf(-1f, 1f)) {
                val base = Offset(body.x + side * bx * 0.66f, body.y - by * 0.70f)
                leaf(base, side * 18f + wiggle * side, by * 0.42f, bx * 0.13f)
                leaf(base, side * 70f + wiggle * side, by * 0.40f, bx * 0.13f)
            }
            val rayC = Offset(body.x, body.y - by * 1.32f)
            for ((i, a) in listOf(-28f, 0f, 28f).withIndex()) {
                val len = by * (if (i == 1) 0.24f else 0.17f)
                val tw = 0.6f + 0.4f * sin(time * 3f + i)
                rotate(a, pivot = Offset(body.x, body.y)) {
                    drawLine(
                        Color(0xFFFFF0B8).copy(alpha = 0.9f * tw),
                        Offset(rayC.x, rayC.y), Offset(rayC.x, rayC.y - len),
                        strokeWidth = bx * 0.035f, cap = StrokeCap.Round
                    )
                }
            }
        }
        Sprouts.Stems -> {
            // A single leaf on a thin stem each side (the To-do mascot)
            for (side in listOf(-1f, 1f)) {
                val root = Offset(body.x + side * bx * 0.62f, body.y - by * 0.72f)
                val top = Offset(root.x + side * bx * 0.10f + wiggle * 0.6f, root.y - by * 0.42f)
                val stem = Path().apply {
                    moveTo(root.x, root.y)
                    quadraticBezierTo(root.x + side * bx * 0.02f, root.y - by * 0.25f, top.x, top.y)
                }
                drawPath(stem, Color(0xFFE6ECFF).copy(alpha = 0.9f), style = Stroke(width = bx * 0.025f, cap = StrokeCap.Round))
                leaf(top, side * 28f + wiggle * side, by * 0.40f, bx * 0.13f)
            }
        }
        Sprouts.None -> Unit
    }
}

private fun DrawScope.drawFace(body: Offset, bx: Float, by: Float, face: Face, pose: BubblePose, l: FaceLayout) {
    val ink = Sky.FaceInk
    val s = bx * l.eyeSize
    val look = Offset(pose.look.x * bx * 0.04f, pose.look.y * by * 0.04f)
    val eyeY = body.y + by * l.eyeDy
    val stroke = s * 0.055f

    // Blush first, so eyes sit on top
    if (face.blush > 0f) {
        for (side in listOf(-1f, 1f)) {
            val bc = Offset(body.x + side * bx * l.blushDx, body.y + by * l.blushDy)
            drawOval(
                Brush.radialGradient(
                    listOf(Sky.Blush.copy(alpha = 0.85f * face.blush), Sky.Blush.copy(alpha = 0f)),
                    center = bc, radius = s * 0.16f
                ),
                topLeft = Offset(bc.x - s * 0.16f, bc.y - s * 0.10f), size = Size(s * 0.32f, s * 0.20f)
            )
        }
    }

    for (side in listOf(-1f, 1f)) {
        val e = Offset(body.x + side * bx * l.eyeDx, eyeY) + look
        val kind = when {
            face.eyes == Eyes.Wink && side < 0 -> Eyes.Happy
            face.eyes == Eyes.Wink -> Eyes.Open
            else -> face.eyes
        }
        when (kind) {
            Eyes.Happy -> {
                val w = s * 0.13f
                val p = Path().apply {
                    moveTo(e.x - w, e.y + w * 0.35f)
                    quadraticBezierTo(e.x, e.y - w * 0.9f, e.x + w, e.y + w * 0.35f)
                }
                drawPath(p, ink, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Eyes.Sleepy -> {
                val w = s * 0.11f
                val p = Path().apply {
                    moveTo(e.x - w, e.y)
                    quadraticBezierTo(e.x, e.y + w * 0.7f, e.x + w, e.y)
                }
                drawPath(p, ink, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
            }
            Eyes.Hearts -> heartShape(e, s * 0.09f, Color(0xFFF0528E))
            Eyes.Stars -> sparkle(e, s * 0.12f, Color(0xFFFFC94D))
            Eyes.Open, Eyes.Wide, Eyes.Wink -> {
                val big = kind == Eyes.Wide
                val ew = s * (if (big) 0.12f else 0.095f)
                val eh = s * (if (big) 0.15f else 0.12f) * pose.eyeOpen.coerceIn(0.08f, 1f)
                drawOval(ink, topLeft = Offset(e.x - ew / 2, e.y - eh / 2), size = Size(ew, eh))
                if (pose.eyeOpen > 0.5f) {
                    drawCircle(Color.White, radius = ew * 0.26f, center = Offset(e.x + ew * 0.16f, e.y - eh * 0.18f))
                    drawCircle(Color.White.copy(alpha = 0.6f), radius = ew * 0.12f, center = Offset(e.x - ew * 0.14f, e.y + eh * 0.22f))
                }
            }
        }
    }

    val m = Offset(body.x, body.y + by * l.mouthDy) + look * 0.5f
    val ms = s * l.mouthSize
    when (face.mouth) {
        Mouth.Small -> {
            val p = Path().apply {
                moveTo(m.x - ms * 0.05f, m.y)
                quadraticBezierTo(m.x, m.y + ms * 0.05f, m.x + ms * 0.05f, m.y)
            }
            drawPath(p, ink, style = Stroke(stroke * 0.85f, cap = StrokeCap.Round))
        }
        Mouth.Smile -> {
            val p = Path().apply {
                moveTo(m.x - ms * 0.08f, m.y - ms * 0.01f)
                quadraticBezierTo(m.x, m.y + ms * 0.08f, m.x + ms * 0.08f, m.y - ms * 0.01f)
            }
            drawPath(p, ink, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
        }
        Mouth.Grin, Mouth.Laugh -> {
            val w = ms * (if (face.mouth == Mouth.Laugh) 0.13f else 0.08f)
            val h = ms * (if (face.mouth == Mouth.Laugh) 0.13f else 0.08f)
            val p = Path().apply {
                moveTo(m.x - w, m.y - h * 0.15f)
                cubicTo(m.x - w * 0.9f, m.y + h * 1.05f, m.x + w * 0.9f, m.y + h * 1.05f, m.x + w, m.y - h * 0.15f)
                close()
            }
            drawPath(p, Sky.Mouth)
            clipPath(p) {
                drawOval(Sky.Tongue, topLeft = Offset(m.x - w * 0.62f, m.y + h * 0.40f), size = Size(w * 1.24f, h * 0.9f))
            }
        }
        Mouth.Oh -> {
            drawOval(Sky.Mouth, topLeft = Offset(m.x - ms * 0.04f, m.y - ms * 0.02f), size = Size(ms * 0.08f, ms * 0.09f))
        }
        Mouth.Wobbly -> {
            val w = ms * 0.08f
            val p = Path().apply {
                moveTo(m.x - w, m.y + w * 0.2f)
                quadraticBezierTo(m.x - w * 0.5f, m.y - w * 0.3f, m.x, m.y + w * 0.1f)
                quadraticBezierTo(m.x + w * 0.5f, m.y + w * 0.5f, m.x + w, m.y)
            }
            drawPath(p, ink, style = Stroke(stroke * 0.85f, cap = StrokeCap.Round))
        }
        Mouth.Flat -> drawLine(ink, Offset(m.x - ms * 0.05f, m.y), Offset(m.x + ms * 0.05f, m.y), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
    }
}

fun DrawScope.heartShape(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + s * 0.9f)
        cubicTo(c.x - s * 1.4f, c.y - s * 0.1f, c.x - s * 0.6f, c.y - s * 1.1f, c.x, c.y - s * 0.35f)
        cubicTo(c.x + s * 0.6f, c.y - s * 1.1f, c.x + s * 1.4f, c.y - s * 0.1f, c.x, c.y + s * 0.9f)
        close()
    }
    drawPath(p, color)
}

/** Four-point sparkle (✦). */
fun DrawScope.sparkle(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - s)
        quadraticBezierTo(c.x + s * 0.12f, c.y - s * 0.12f, c.x + s, c.y)
        quadraticBezierTo(c.x + s * 0.12f, c.y + s * 0.12f, c.x, c.y + s)
        quadraticBezierTo(c.x - s * 0.12f, c.y + s * 0.12f, c.x - s, c.y)
        quadraticBezierTo(c.x - s * 0.12f, c.y - s * 0.12f, c.x, c.y - s)
        close()
    }
    drawPath(p, color)
}

/** A floating petal, used drifting around the mascot. */
fun DrawScope.petal(c: Offset, s: Float, angle: Float, alpha: Float = 0.9f) {
    rotate(angle, pivot = c) {
        val p = Path().apply {
            moveTo(c.x, c.y - s)
            quadraticBezierTo(c.x + s * 0.75f, c.y - s * 0.15f, c.x, c.y + s)
            quadraticBezierTo(c.x - s * 0.75f, c.y - s * 0.15f, c.x, c.y - s)
            close()
        }
        drawPath(p, Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE9D9FB), Color(0xFFF6C6E4)), start = Offset(c.x, c.y - s), end = Offset(c.x, c.y + s)), alpha = alpha)
        drawLine(Color.White.copy(alpha = 0.7f * alpha), Offset(c.x, c.y - s * 0.7f), Offset(c.x, c.y + s * 0.7f), strokeWidth = s * 0.06f)
    }
}

/** Unit circle point at [deg] (0 = 12 o'clock, clockwise). */
fun clockPoint(c: Offset, r: Float, deg: Float): Offset {
    val a = (deg - 90f) * PI.toFloat() / 180f
    return Offset(c.x + cos(a) * r, c.y + sin(a) * r)
}
