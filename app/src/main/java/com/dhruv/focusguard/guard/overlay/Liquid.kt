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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.guard.Haptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════
//  Liquid tile: touch it and a glossy drop of light pools under your
//  finger, trails behind it like mercury, stretches in the direction
//  you move, and ripples out with a jelly wobble when you let go.
// ═════════════════════════════════════════════════════════════════

@Composable
fun LiquidTile(
    background: Color,
    liquid: Color,
    haptics: Haptics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    val blob = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val ripple = remember { Animatable(1f) }
    var rippleAt by remember { mutableStateOf(Offset.Zero) }
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    val shape = RoundedCornerShape(radius)

    Box(
        modifier
            .graphicsLayer {
                scaleX = sx.value
                scaleY = sy.value
            }
            .shadow(8.dp, shape, clip = false, ambientColor = liquid.copy(alpha = 0.3f), spotColor = liquid.copy(alpha = 0.35f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.55f), background.copy(alpha = 0.92f), background)
                )
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    haptics.tick()
                    scope.launch { blob.snapTo(down.position) }
                    scope.launch { press.animateTo(1f, spring(0.55f, 320f)) }
                    scope.launch { sx.animateTo(0.95f, spring(0.5f, 520f)) }
                    scope.launch { sy.animateTo(0.92f, spring(0.5f, 520f)) }
                    var released = false
                    var inside = true
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            released = true
                            break
                        }
                        // Lagging spring = the drop trails your finger like a liquid
                        scope.launch { blob.animateTo(ch.position, spring(0.42f, 160f)) }
                        inside = ch.position.x in 0f..size.width.toFloat() &&
                            ch.position.y in 0f..size.height.toFloat()
                        ch.consume()
                    }
                    rippleAt = blob.value
                    scope.launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                    scope.launch { press.animateTo(0f, tween(420)) }
                    // Low damping = a visible jelly wobble as it settles
                    scope.launch { sx.animateTo(1.04f, tween(90)); sx.animateTo(1f, spring(0.22f, 240f)) }
                    scope.launch { sy.animateTo(1.06f, tween(90)); sy.animateTo(1f, spring(0.22f, 240f)) }
                    if (released && inside) {
                        haptics.click()
                        onClick()
                    }
                }
            }
            .drawWithContent {
                val p = press.value
                // Pooled liquid under the finger, stretched along its motion
                if (p > 0.01f) {
                    val v = blob.velocity
                    val speed = min(v.getDistance() / 2500f, 0.9f)
                    val angle = Math.toDegrees(atan2(v.y, v.x).toDouble()).toFloat()
                    val r = size.minDimension * (0.30f + 0.40f * p)
                    rotate(angle, pivot = blob.value) {
                        scale(1f + speed, 1f / (1f + speed * 0.6f), pivot = blob.value) {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(liquid.copy(alpha = 0.55f * p), liquid.copy(alpha = 0.18f * p), Color.Transparent),
                                    center = blob.value, radius = r
                                ),
                                radius = r, center = blob.value
                            )
                            // Mercury-like specular bead
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(Color.White.copy(alpha = 0.75f * p), Color.Transparent),
                                    center = blob.value - Offset(r * 0.18f, r * 0.22f), radius = r * 0.35f
                                ),
                                radius = r * 0.35f, center = blob.value - Offset(r * 0.18f, r * 0.22f)
                            )
                        }
                    }
                }
                drawContent()
                // Ripple ring on release
                val rp = ripple.value
                if (rp < 1f) {
                    val maxR = max(size.width, size.height) * 1.1f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.65f * (1f - rp)),
                        radius = maxR * rp,
                        center = rippleAt,
                        style = Stroke(width = 3.dp.toPx() * (1f - rp) + 0.5f)
                    )
                    drawCircle(
                        color = liquid.copy(alpha = 0.18f * (1f - rp)),
                        radius = maxR * rp * 0.8f,
                        center = rippleAt
                    )
                }
                // Meniscus: the rim catches light while pressed
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.55f + 0.35f * p),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(1.2.dp.toPx())
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 10.dp)
                .graphicsLayer {
                    val s = 1f + press.value * 0.05f
                    scaleX = s
                    scaleY = s
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

// ═════════════════════════════════════════════════════════════════
//  Liquid "Continue to Instagram". Locked: pressing it shakes it with
//  a double tick. Unlocked: press and hold, and a glowing liquid with
//  a rolling wave front fills it left to right while haptic ticks
//  speed up. Let go early and it drains back; fill it and you're in.
// ═════════════════════════════════════════════════════════════════

private const val HOLD_MS = 1400

@Composable
fun LiquidHoldButton(
    unlocked: Boolean,
    secondsLeftLabel: String,
    appIcon: ImageBitmap?,
    haptics: Haptics,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val fill = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val squeeze = remember { Animatable(1f) }
    var flashLocked by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    val t = rememberInfiniteTransition(label = "cta")
    val phase by t.animateFloat(0f, 6.283f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "wave")
    val shimmer by t.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "shimmer")
    val rays by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rays")

    Box(
        modifier
            .drawBehind {
                // Rays at both ends, like the mockup
                val cy = size.height / 2f
                val len = 9.dp.toPx()
                val gap = 10.dp.toPx()
                val c = Color.White.copy(alpha = 0.85f * rays)
                for (deg in listOf(-35f, 0f, 35f)) {
                    val rad = Math.toRadians(deg.toDouble())
                    val dx = cos(rad).toFloat()
                    val dy = sin(rad).toFloat()
                    drawLine(c, Offset(-gap * dx, cy - gap * dy), Offset(-(gap + len) * dx, cy - (gap + len) * dy), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(c, Offset(size.width + gap * dx, cy - gap * dy), Offset(size.width + (gap + len) * dx, cy - (gap + len) * dy), 2.dp.toPx(), StrokeCap.Round)
                }
                // End glows
                drawCircle(
                    Brush.radialGradient(listOf(P.CtaRim.copy(alpha = 0.55f), Color.Transparent), center = Offset(0f, cy), radius = size.height * 0.9f),
                    radius = size.height * 0.9f, center = Offset(0f, cy)
                )
                drawCircle(
                    Brush.radialGradient(listOf(P.LiquidTo.copy(alpha = 0.45f), Color.Transparent), center = Offset(size.width, cy), radius = size.height * 0.9f),
                    radius = size.height * 0.9f, center = Offset(size.width, cy)
                )
            }
            .graphicsLayer {
                translationX = shake.value * 10.dp.toPx()
                scaleX = squeeze.value
                scaleY = 2f - squeeze.value
            }
            .shadow(22.dp, shape, clip = false, ambientColor = P.CtaGlow, spotColor = P.CtaGlow)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(P.CtaTop, P.CtaMid, P.CtaBottom)))
            .pointerInput(unlocked) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (!unlocked) {
                        haptics.tick()
                        flashLocked = true
                        scope.launch {
                            for (x in listOf(-1f, 0.8f, -0.6f, 0.4f, -0.2f, 0f)) shake.animateTo(x, tween(55))
                        }
                        scope.launch { delay(120); haptics.tick() }
                        // wait for release
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.none { it.pressed }) break
                        }
                        scope.launch { delay(1400); flashLocked = false }
                        return@awaitEachGesture
                    }
                    haptics.click()
                    scope.launch { squeeze.animateTo(0.97f, spring(0.5f, 500f)) }
                    var done = false
                    val fillJob: Job = scope.launch {
                        val remaining = ((1f - fill.value) * HOLD_MS).toInt().coerceAtLeast(1)
                        fill.animateTo(1f, tween(remaining, easing = LinearEasing))
                        done = true
                        haptics.thud()
                        squeeze.animateTo(1.04f, tween(90))
                        squeeze.animateTo(1f, spring(0.3f, 300f))
                        onComplete()
                    }
                    val tickJob: Job = scope.launch {
                        while (true) {
                            haptics.tick()
                            delay((170 - 120 * fill.value).toLong().coerceAtLeast(45L))
                        }
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.none { it.pressed }) break
                        ev.changes.forEach { it.consume() }
                    }
                    tickJob.cancel()
                    if (!done) {
                        fillJob.cancel()
                        scope.launch { fill.animateTo(0f, spring(0.55f, 90f)) }
                        scope.launch { squeeze.animateTo(1f, spring(0.25f, 300f)) }
                    }
                }
            }
            .drawWithContent {
                val w = size.width
                val h = size.height
                // Idle liquid sheen drifting across
                val sx0 = shimmer * w
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                        startX = sx0 - w * 0.25f, endX = sx0 + w * 0.25f
                    )
                )
                // Liquid fill with a rolling wave front
                val f = fill.value
                if (f > 0.001f) {
                    val front = f * (w + h * 0.4f)
                    val amp = h * 0.10f * (1f - f * 0.5f)
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        var y = 0f
                        val step = h / 12f
                        while (y <= h) {
                            lineTo(front + amp * sin(y / h * 6.283f * 1.2f + phase), y)
                            y += step
                        }
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(path, Brush.horizontalGradient(listOf(P.LiquidFrom, P.LiquidMid, P.LiquidTo), startX = 0f, endX = max(front, 1f)), alpha = 0.92f)
                    // Bubbles rising inside the liquid
                    for (i in 0 until 6) {
                        val bx = (i * 0.17f + 0.05f) * front
                        val by = h - ((phase / 6.283f + i * 0.21f) % 1f) * h
                        drawCircle(Color.White.copy(alpha = 0.35f), radius = h * 0.035f * (1 + i % 2), center = Offset(bx, by))
                    }
                }
                // Glass gloss on the top half
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = h * 0.55f),
                    size = Size(w, h * 0.55f),
                    cornerRadius = CornerRadius(h / 2f)
                )
                drawContent()
                // Rim
                drawRoundRect(
                    Brush.horizontalGradient(listOf(P.CtaRim.copy(alpha = 0.9f), Color.White.copy(alpha = 0.35f), P.LiquidTo.copy(alpha = 0.8f))),
                    cornerRadius = CornerRadius(h / 2f),
                    style = Stroke(1.6.dp.toPx())
                )
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(appIcon, 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (flashLocked && !unlocked) "Unlocks in $secondsLeftLabel" else "Continue to Instagram",
                style = BaseText,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (unlocked) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(secondsLeftLabel, style = Tabular, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The installed app's own icon (falls back to a soft gradient tile). */
@Composable
fun AppIcon(icon: ImageBitmap?, size: Dp) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = "Instagram",
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.26f))
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.26f))
                .background(Brush.linearGradient(listOf(Color(0xFFFEC564), Color(0xFFF2416C), Color(0xFF8A3AB9))))
        )
    }
}
