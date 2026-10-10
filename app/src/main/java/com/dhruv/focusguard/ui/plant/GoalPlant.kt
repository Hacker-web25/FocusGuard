package com.dhruv.focusguard.ui.plant

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dhruv.focusguard.guard.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ═════════════════════════════════════════════════════════════════
//  A plant that grows with a goal.
//
//   0%   a seed resting on the soil
//   5%   it cracks open; a sprout pushes up with two seed leaves
//  20%+  the stem climbs and true leaves unfurl one by one
//  70%+  a green bud forms at the top and blushes pink
//  90%+  the petals start to peek out
// 100%   full bloom: the bud bursts open with a glow, falling petals
//        and a success haptic
//
// Every increase is animated (it visibly grows), each new leaf lands with
// a click haptic and a few sparkles, and you can tap it to make it sway.
// Drawn entirely in code, so it is crisp at any size and never "loads".
// ═════════════════════════════════════════════════════════════════

/** Name for where a goal is, shown next to its plant. */
fun plantStage(progress: Int): String = when {
    progress >= 100 -> "In full bloom"
    progress >= 90 -> "About to bloom"
    progress >= 70 -> "Budding"
    progress >= 45 -> "Growing tall"
    progress >= 20 -> "Seedling"
    progress >= 5 -> "Sprouting"
    else -> "Seed planted"
}

fun plantEmoji(progress: Int): String = when {
    progress >= 100 -> "🌸"
    progress >= 70 -> "🌷"
    progress >= 5 -> "🌱"
    else -> "🫘"
}

/**
 * @param progress goal progress, 0..100.
 * @param celebrate play the bloom celebration when progress reaches 100 while shown.
 * @param onBloomed called once the bloom animation has opened.
 */
@Composable
fun GoalPlant(
    progress: Int,
    modifier: Modifier = Modifier,
    haptics: Haptics? = null,
    interactive: Boolean = true,
    celebrate: Boolean = true,
    onBloomed: (() -> Unit)? = null
) {
    val target = progress.coerceIn(0, 100) / 100f
    val scope = rememberCoroutineScope()
    val grow = remember { Animatable(target) }
    val bloom = remember { Animatable(if (target >= 1f) 1f else 0f) }
    val wiggle = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val bits = remember { mutableStateListOf<Bit>() }
    var clock by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val geo = remember { PlantGeo() }
    val bloomedCallback by rememberUpdatedState(onBloomed)

    val idle = rememberInfiniteTransition(label = "plant")
    val swayPhase by idle.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(4800, easing = LinearEasing)), label = "sway"
    )
    val pulse by idle.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )

    // Particle clock: runs only while sparkles or petals are in the air.
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow { bits.size }.first { it > 0 }
            while (bits.isNotEmpty()) {
                withFrameMillis { }
                val now = SystemClock.uptimeMillis()
                clock = now
                bits.removeAll { now - it.born > it.life }
            }
        }
    }

    // Growing (or shrinking back) whenever progress changes.
    LaunchedEffect(target) {
        val from = grow.value
        if (abs(target - from) < 0.0005f) {
            if (target >= 1f && bloom.value < 1f) bloom.snapTo(1f)
            return@LaunchedEffect
        }
        val up = target > from
        if (up) {
            haptics?.tick()
            launch { sway(wiggle, if (Random.nextBoolean()) 1f else -1f) }
            sprinkle(bits, geo, 10)
        }
        if (target < 1f && bloom.value > 0f) {
            launch { bloom.animateTo(0f, tween(700, easing = FastOutSlowInEasing)) }
        }
        var stage = stageOf(from)
        val ms = (900 + 1700 * abs(target - from)).toInt().coerceAtMost(2400)
        grow.animateTo(target, tween(ms, easing = FastOutSlowInEasing)) {
            val s = stageOf(value)
            if (s > stage) {
                haptics?.click()
                sprinkle(bits, geo, 6)
            }
            stage = s
        }
        if (target >= 1f) {
            if (up && celebrate) {
                haptics?.success()
                launch {
                    flash.snapTo(1f)
                    flash.animateTo(0f, tween(1700, easing = FastOutSlowInEasing))
                }
                petalBurst(bits, geo)
                bloom.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 38f))
                bloomedCallback?.invoke()
            } else {
                bloom.snapTo(1f)
            }
        }
    }

    // A bloomed flower now and then lets a sparkle drift up.
    LaunchedEffect(target >= 1f) {
        if (target < 1f) return@LaunchedEffect
        while (true) {
            delay(2600L + Random.nextLong(1800L))
            if (bloom.value > 0.9f) sprinkle(bits, geo, 1, nearFlower = true)
        }
    }

    val touch = if (interactive) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { pos ->
                haptics?.tick()
                val dir = if (pos.x < size.width / 2f) 1f else -1f
                scope.launch { sway(wiggle, dir) }
                sprinkle(bits, geo, 5, nearFlower = bloom.value > 0.5f)
            }
        }
    } else Modifier

    Canvas(
        modifier
            .semantics { contentDescription = "Goal plant: ${plantStage(progress)}, $progress percent" }
            .then(touch)
    ) {
        drawPlant(
            g = grow.value,
            b = bloom.value,
            sway = sin(swayPhase),
            wiggle = wiggle.value,
            pulse = pulse,
            flash = flash.value,
            geo = geo
        )
        drawBits(bits, clock, geo)
    }
}

/** A quick push to one side, then a springy settle (low damping = it wobbles). */
private suspend fun sway(a: Animatable<Float, AnimationVector1D>, dir: Float) {
    a.snapTo(0f)
    a.animateTo(dir, tween(130, easing = FastOutSlowInEasing))
    a.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 110f))
}

// ═════════════════════════════════════════════════════════════════
//  Growth math
// ═════════════════════════════════════════════════════════════════

private val STAGES = floatArrayOf(0.05f, 0.20f, 0.36f, 0.50f, 0.62f, 0.72f, 0.86f, 0.95f)
private fun stageOf(g: Float) = STAGES.count { g >= it - 0.0001f }

private fun seg(x: Float, a: Float, b: Float) = ((x - a) / (b - a)).coerceIn(0f, 1f)
private fun smooth(x: Float) = x * x * (3f - 2f * x)
private fun ease(x: Float, a: Float, b: Float) = smooth(seg(x, a, b))
private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Overshoots a little before settling: leaves "pop" open. */
private fun backOut(x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val t = x - 1f
    return 1f + c3 * t * t * t + c1 * t * t
}

private fun bez(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val m = 1f - t
    val a = m * m * m
    val b = 3f * m * m * t
    val c = 3f * m * t * t
    val d = t * t * t
    return Offset(
        a * p0.x + b * p1.x + c * p2.x + d * p3.x,
        a * p0.y + b * p1.y + c * p2.y + d * p3.y
    )
}

private fun bezAngle(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Float {
    val m = 1f - t
    val dx = 3f * m * m * (p1.x - p0.x) + 6f * m * t * (p2.x - p1.x) + 3f * t * t * (p3.x - p2.x)
    val dy = 3f * m * m * (p1.y - p0.y) + 6f * m * t * (p2.y - p1.y) + 3f * t * t * (p3.y - p2.y)
    return if (dx == 0f && dy == 0f) (-PI / 2).toFloat() else atan2(dy, dx)
}

/** Where things are, written while drawing so particles can be spawned in the right place. */
private class PlantGeo {
    var ready = false
    var cx = 0f
    var soilY = 0f
    var tipX = 0f
    var tipY = 0f
    var unit = 0f
    var flowerX = 0f
    var flowerY = 0f
}

// Leaves: where on the stem, which side, when they appear, how big.
private val LEAF_T = floatArrayOf(0.36f, 0.52f, 0.66f, 0.79f)
private val LEAF_SIDE = floatArrayOf(-1f, 1f, -1f, 1f)
private val LEAF_AT = floatArrayOf(0.22f, 0.36f, 0.50f, 0.62f)
private val LEAF_SIZE = floatArrayOf(1.0f, 0.94f, 0.82f, 0.68f)

private val LeafDark = Color(0xFF3A9663)
private val LeafLight = Color(0xFFA4E6AE)
private val LeafDark2 = Color(0xFF2F8458)
private val LeafLight2 = Color(0xFF8ED89C)
private val SeedLeafDark = Color(0xFF6CC283)
private val SeedLeafLight = Color(0xFFCDF3BF)
private val StemTop = Color(0xFF94DCA2)
private val StemBottom = Color(0xFF3B935F)
private val BudBase = Color(0xFFA6DFA6)
private val BudTip = Color(0xFF5DB878)
private val PetalBase = Color(0xFFFFEEF4)
private val PetalTip = Color(0xFFFF86B1)
private val InnerBase = Color(0xFFFFFFFF)
private val InnerTip = Color(0xFFFFB3CE)
// Green → warm peach → pink, so the bud never passes through grey.
private val BudBaseMid = Color(0xFFFFF0DA)
private val BudTipMid = Color(0xFFF6BE86)
private val GlowPink = Color(0xFFFFD3E4)
private val ShadowInk = Color(0xFF402A55)

private fun blush(start: Color, mid: Color, end: Color, t: Float): Color =
    if (t < 0.5f) lerp(start, mid, t * 2f) else lerp(mid, end, (t - 0.5f) * 2f)

// ═════════════════════════════════════════════════════════════════
//  Drawing
// ═════════════════════════════════════════════════════════════════

private fun DrawScope.drawPlant(
    g: Float,
    b: Float,
    sway: Float,
    wiggle: Float,
    pulse: Float,
    flash: Float,
    geo: PlantGeo
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    // Pot
    val potW = min(w * 0.60f, h * 0.44f)
    val potH = potW * 0.64f
    val rimH = potH * 0.20f
    val potTop = h - potH - h * 0.03f
    val soilY = potTop + rimH * 0.42f

    // Flower size and the tallest the stem can get
    val fr = min(w * 0.17f, h * 0.12f)
    val maxStem = (soilY - fr * 1.45f - h * 0.02f).coerceAtLeast(fr)

    // Stem: a soft S-curve that sways at the top
    val stemF = 0.10f * ease(g, 0.02f, 0.12f) + 0.90f * ease(g, 0.10f, 0.88f)
    val len = maxStem * stemF
    val swayX = (sway * 0.045f + wiggle * 0.11f) * len
    val p0 = Offset(cx, soilY)
    val p1 = Offset(cx + len * 0.10f, soilY - len * 0.36f)
    val p2 = Offset(cx - len * 0.09f + swayX * 0.55f, soilY - len * 0.70f)
    val p3 = Offset(cx + swayX, soilY - len)

    // Flower geometry (needed early for the glow behind everything)
    val fs = ease(g, 0.68f, 0.86f)
    val up = bezAngle(p0, p1, p2, p3, 1f)
    val radius = fr * fs * (1f + 0.025f * b * pulse)
    val fcx = p3.x + cos(up) * radius * 0.12f
    val fcy = p3.y + sin(up) * radius * 0.12f

    geo.ready = true
    geo.cx = cx
    geo.soilY = soilY
    geo.tipX = p3.x
    geo.tipY = p3.y
    geo.unit = fr
    geo.flowerX = fcx
    geo.flowerY = fcy

    // Glow behind the bloom
    val glowA = (0.50f * b * (0.75f + 0.25f * pulse) + 0.60f * flash).coerceIn(0f, 1f)
    if (glowA > 0.01f && fs > 0.01f) {
        val gr = fr * (2.0f + 1.6f * flash)
        // Every stop is the same pink at falling alpha: fading to "transparent
        // black" would leave a grey ring at the edge.
        drawCircle(
            Brush.radialGradient(
                0f to GlowPink.copy(alpha = glowA),
                0.35f to GlowPink.copy(alpha = glowA * 0.45f),
                0.70f to GlowPink.copy(alpha = glowA * 0.12f),
                1f to GlowPink.copy(alpha = 0f),
                center = Offset(fcx, fcy), radius = gr
            ),
            radius = gr, center = Offset(fcx, fcy)
        )
    }

    drawPot(cx, potTop, potW, potH, rimH)

    // Seed (fades as the sprout takes over)
    val seedA = 1f - ease(g, 0.04f, 0.12f)
    if (seedA > 0.01f) {
        val sw = fr * 0.62f
        val sh = fr * 0.40f
        val sc = Offset(cx - fr * 0.06f, soilY - sh * 0.30f)
        rotate(-18f, pivot = sc) {
            drawOval(
                Brush.verticalGradient(
                    listOf(Color(0xFFD39A68).copy(alpha = seedA), Color(0xFF7D5133).copy(alpha = seedA)),
                    startY = sc.y - sh / 2f, endY = sc.y + sh / 2f
                ),
                topLeft = Offset(sc.x - sw / 2f, sc.y - sh / 2f),
                size = Size(sw, sh)
            )
            drawOval(
                Color.White.copy(alpha = 0.45f * seedA),
                topLeft = Offset(sc.x - sw * 0.28f, sc.y - sh * 0.36f),
                size = Size(sw * 0.30f, sh * 0.22f)
            )
        }
    }

    if (len < 0.6f) return

    // Stem
    val stem = Path().apply {
        moveTo(p0.x, p0.y)
        cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
    }
    val stemW = max(1.5f, fr * mix(0.09f, 0.20f, stemF))
    drawPath(
        stem,
        Brush.verticalGradient(listOf(StemTop, StemBottom), startY = p3.y, endY = p0.y),
        style = Stroke(width = stemW, cap = StrokeCap.Round)
    )

    // Seed leaves: ride up with the first sprout, then settle low on the stem
    val coty = ease(g, 0.04f, 0.16f) * (1f - 0.30f * ease(g, 0.50f, 0.90f))
    if (coty > 0.01f) {
        val tc = mix(1f, 0.16f, ease(g, 0.12f, 0.42f))
        val at = bez(p0, p1, p2, p3, tc)
        val ang = bezAngle(p0, p1, p2, p3, tc)
        val open = mix(0.25f, 1.15f, ease(g, 0.05f, 0.18f))
        val l = fr * 0.95f * coty
        leaf(at, ang - open + sway * 0.05f, l, l * 0.40f, SeedLeafLight, SeedLeafDark)
        leaf(at, ang + open + sway * 0.05f, l, l * 0.40f, SeedLeafLight, SeedLeafDark)
    }

    // True leaves, unfurling one after another
    for (i in LEAF_T.indices) {
        val s = backOut(seg(g, LEAF_AT[i], LEAF_AT[i] + 0.14f))
        if (s <= 0.01f) continue
        val t = LEAF_T[i]
        val at = bez(p0, p1, p2, p3, t)
        val ang = bezAngle(p0, p1, p2, p3, t)
        val side = LEAF_SIDE[i]
        val spread = mix(0.18f, 1.05f, ease(g, LEAF_AT[i], LEAF_AT[i] + 0.20f))
        val l = fr * 1.30f * LEAF_SIZE[i] * s
        val dark = if (i % 2 == 0) LeafDark else LeafDark2
        val light = if (i % 2 == 0) LeafLight else LeafLight2
        leaf(at, ang + side * spread + sway * 0.06f * side + wiggle * 0.10f, l, l * 0.32f, light, dark)
    }

    // Bud → flower
    if (fs > 0.01f) {
        val m = ease(g, 0.80f, 0.97f)
        val peek = 0.16f * ease(g, 0.90f, 1.0f)
        val o = peek + (1f - peek) * b
        val center = Offset(fcx, fcy)

        // Sepals cradle the bud and fold back as it opens
        val sepal = mix(0.45f, 1.30f, o)
        val sl = radius * 0.58f
        leaf(p3, up - sepal, sl, sl * 0.34f, LeafLight, LeafDark, rib = false)
        leaf(p3, up + sepal, sl, sl * 0.34f, LeafLight, LeafDark, rib = false)

        // Outer petals: clustered into a bud, then fanned into a full circle.
        // Drawn outermost first so the middle ones sit in front of the bud.
        val baseCol = blush(BudBase, BudBaseMid, PetalBase, m)
        val tipCol = blush(BudTip, BudTipMid, PetalTip, m)
        for (i in intArrayOf(0, 5, 1, 4, 2, 3)) {
            val rel = mix((i - 2.5f) * 0.16f, (i - 2.5f) * 1.0472f, o)
            val pl = radius * mix(0.66f, 1.12f, o)
            petal(center, up + rel, pl, pl * mix(0.36f, 0.50f, o), baseCol, tipCol)
        }

        // Inner petals open later, lighter
        val innerA = ease(o, 0.25f, 0.60f)
        if (innerA > 0.01f) {
            val ib = blush(BudBase, BudBaseMid, InnerBase, m).copy(alpha = innerA)
            val it2 = blush(BudTip, BudTipMid, InnerTip, m).copy(alpha = innerA)
            for (j in intArrayOf(0, 4, 1, 3, 2)) {
                val rel = mix((j - 2f) * 0.10f, (j - 2f) * 1.2566f, o)
                val pl = radius * mix(0.45f, 0.72f, o)
                petal(center, up + rel, pl, pl * 0.46f, ib, it2)
            }
        }

        // Golden heart and stamens
        val ca = ease(o, 0.35f, 0.75f)
        if (ca > 0.01f) {
            val dr = radius * 0.24f * ca
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFF5CC), Color(0xFFFFC44F), Color(0xFFF2992B)),
                    center = center, radius = dr
                ),
                radius = dr, center = center
            )
            for (k in 0 until 10) {
                val a = k * 0.6283f + 0.3f
                val r = radius * 0.34f * ca
                drawCircle(
                    Color(0xFFFFD86E).copy(alpha = ca),
                    radius = radius * 0.045f,
                    center = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                )
            }
            drawCircle(Color.White.copy(alpha = 0.7f * ca), radius = dr * 0.28f, center = Offset(center.x - dr * 0.3f, center.y - dr * 0.3f))
        } else if (o < 0.3f) {
            // Shine on the closed bud
            val hl = Offset(center.x + cos(up) * radius * 0.40f - radius * 0.12f, center.y + sin(up) * radius * 0.40f)
            drawCircle(Color.White.copy(alpha = 0.40f * fs), radius = radius * 0.09f, center = hl)
        }
    }
}

/** Glossy pastel ceramic pot with a soil top. */
private fun DrawScope.drawPot(cx: Float, top: Float, potW: Float, potH: Float, rimH: Float) {
    val bTop = top + rimH * 0.8f
    val bBot = top + potH
    val topW = potW * 0.90f
    val botW = potW * 0.66f

    // Soft contact shadow: a radial fade squashed into an ellipse
    scale(scaleX = 1f, scaleY = 0.20f, pivot = Offset(cx, bBot)) {
        drawCircle(
            Brush.radialGradient(
                listOf(ShadowInk.copy(alpha = 0.34f), ShadowInk.copy(alpha = 0.12f), ShadowInk.copy(alpha = 0f)),
                center = Offset(cx, bBot), radius = potW * 0.56f
            ),
            radius = potW * 0.56f, center = Offset(cx, bBot)
        )
    }

    val body = Path().apply {
        moveTo(cx - topW / 2f, bTop)
        lineTo(cx + topW / 2f, bTop)
        cubicTo(
            cx + topW / 2f, bTop + (bBot - bTop) * 0.55f,
            cx + botW / 2f + potW * 0.02f, bBot - potH * 0.04f,
            cx + botW / 2f - potW * 0.04f, bBot
        )
        lineTo(cx - botW / 2f + potW * 0.04f, bBot)
        cubicTo(
            cx - botW / 2f - potW * 0.02f, bBot - potH * 0.04f,
            cx - topW / 2f, bTop + (bBot - bTop) * 0.55f,
            cx - topW / 2f, bTop
        )
        close()
    }
    drawPath(
        body,
        Brush.horizontalGradient(
            0f to Color(0xFFEFCBDD),
            0.30f to Color(0xFFFFF6FA),
            0.68f to Color(0xFFF0CBDE),
            1f to Color(0xFFD3A2C2),
            startX = cx - topW / 2f, endX = cx + topW / 2f
        )
    )
    // Shade under the rim
    drawRect(
        Color(0x26703F63),
        topLeft = Offset(cx - topW / 2f, bTop),
        size = Size(topW, potH * 0.06f)
    )
    // Lavender band with dots
    val bandY = bTop + (bBot - bTop) * 0.46f
    val bandHalf = mix(topW, botW, 0.46f) / 2f - potW * 0.02f
    drawLine(
        Color(0xFFB79CF0).copy(alpha = 0.75f),
        Offset(cx - bandHalf, bandY), Offset(cx + bandHalf, bandY),
        strokeWidth = potH * 0.055f, cap = StrokeCap.Round
    )
    for (k in -2..2) {
        drawCircle(
            Color.White.copy(alpha = 0.85f),
            radius = potH * 0.028f,
            center = Offset(cx + k * bandHalf * 0.36f, bandY + potH * 0.11f)
        )
    }
    // Glaze highlight
    drawLine(
        Color.White.copy(alpha = 0.55f),
        Offset(cx - topW * 0.30f, bTop + potH * 0.16f),
        Offset(cx - botW * 0.30f, bBot - potH * 0.16f),
        strokeWidth = potW * 0.045f, cap = StrokeCap.Round
    )

    // Rim
    drawRoundRect(
        Brush.horizontalGradient(
            0f to Color(0xFFF4D6E5),
            0.30f to Color.White,
            0.70f to Color(0xFFF2D2E3),
            1f to Color(0xFFDDB0CB),
            startX = cx - potW / 2f, endX = cx + potW / 2f
        ),
        topLeft = Offset(cx - potW / 2f, top),
        size = Size(potW, rimH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rimH / 2f)
    )
    // Soil
    drawOval(
        Brush.verticalGradient(listOf(Color(0xFF7A5442), Color(0xFF4A3127)), startY = top, endY = top + rimH * 0.7f),
        topLeft = Offset(cx - potW * 0.41f, top + rimH * 0.10f),
        size = Size(potW * 0.82f, rimH * 0.60f)
    )
}

private fun DrawScope.leaf(
    base: Offset,
    angle: Float,
    length: Float,
    halfWidth: Float,
    light: Color,
    dark: Color,
    rib: Boolean = true
) {
    if (length < 0.5f) return
    val ca = cos(angle)
    val sa = sin(angle)
    val nx = -sa
    val ny = ca
    val tip = Offset(base.x + ca * length, base.y + sa * length)
    val path = Path().apply {
        moveTo(base.x, base.y)
        cubicTo(
            base.x + ca * length * 0.25f + nx * halfWidth, base.y + sa * length * 0.25f + ny * halfWidth,
            base.x + ca * length * 0.75f + nx * halfWidth * 0.8f, base.y + sa * length * 0.75f + ny * halfWidth * 0.8f,
            tip.x, tip.y
        )
        cubicTo(
            base.x + ca * length * 0.75f - nx * halfWidth * 0.8f, base.y + sa * length * 0.75f - ny * halfWidth * 0.8f,
            base.x + ca * length * 0.25f - nx * halfWidth, base.y + sa * length * 0.25f - ny * halfWidth,
            base.x, base.y
        )
        close()
    }
    drawPath(path, Brush.linearGradient(listOf(dark, light), start = base, end = tip))
    if (rib) {
        drawLine(
            Color.White.copy(alpha = 0.42f),
            base,
            Offset(base.x + ca * length * 0.80f, base.y + sa * length * 0.80f),
            strokeWidth = max(1f, halfWidth * 0.12f),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.petal(center: Offset, angle: Float, length: Float, halfWidth: Float, base: Color, tip: Color) {
    if (length < 0.5f) return
    val dx = cos(angle)
    val dy = sin(angle)
    val nx = -dy
    val ny = dx
    fun at(along: Float, across: Float) = Offset(
        center.x + dx * length * along + nx * halfWidth * across,
        center.y + dy * length * along + ny * halfWidth * across
    )
    val a1 = at(0.15f, 0.90f)
    val a2 = at(0.85f, 1.05f)
    val e1 = at(1.0f, 0.35f)
    val apex = at(1.10f, 0f)
    val e2 = at(1.0f, -0.35f)
    val b1 = at(0.85f, -1.05f)
    val b2 = at(0.15f, -0.90f)
    val path = Path().apply {
        moveTo(center.x, center.y)
        cubicTo(a1.x, a1.y, a2.x, a2.y, e1.x, e1.y)
        quadraticBezierTo(apex.x, apex.y, e2.x, e2.y)
        cubicTo(b1.x, b1.y, b2.x, b2.y, center.x, center.y)
        close()
    }
    drawPath(path, Brush.linearGradient(listOf(base, tip), start = center, end = at(1.05f, 0f)))
    drawLine(
        Color.White.copy(alpha = 0.30f * base.alpha),
        at(0.12f, 0f), at(0.70f, 0f),
        strokeWidth = max(1f, halfWidth * 0.10f), cap = StrokeCap.Round
    )
}

// ═════════════════════════════════════════════════════════════════
//  Particles: sparkles when it grows, falling petals when it blooms
// ═════════════════════════════════════════════════════════════════

private const val SPARKLE = 0
private const val PETAL = 1

private class Bit(
    val x: Float,
    val y: Float,
    val vx: Float,          // px per second
    val vy: Float,
    val born: Long,
    val life: Long,
    val kind: Int,
    val color: Color,
    val spin: Float,        // degrees per second
    val size: Float
)

private val SparkColors = listOf(Color.White, Color(0xFFFFF1A8), Color(0xFFC9F7D2), Color(0xFFFFD1E3))
private val PetalColors = listOf(Color(0xFFFFB3CE), Color(0xFFFF8DB6), Color(0xFFFFD6E5), Color(0xFFFFC2A8))

private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

private fun sprinkle(bits: MutableList<Bit>, geo: PlantGeo, count: Int, nearFlower: Boolean = false) {
    if (!geo.ready) return
    val u = geo.unit
    val now = SystemClock.uptimeMillis()
    repeat(count) {
        val x: Float
        val y: Float
        if (nearFlower) {
            x = geo.flowerX + rnd(-1.2f, 1.2f) * u
            y = geo.flowerY + rnd(-0.8f, 0.6f) * u
        } else {
            x = geo.cx + rnd(-1.6f, 1.6f) * u
            y = mix(geo.tipY, geo.soilY, rnd(0f, 0.9f))
        }
        bits.add(
            Bit(
                x, y,
                vx = rnd(-0.35f, 0.35f) * u,
                vy = -rnd(0.8f, 1.7f) * u,
                born = now + Random.nextLong(160L),
                life = 900L + Random.nextLong(500L),
                kind = SPARKLE,
                color = SparkColors[Random.nextInt(SparkColors.size)],
                spin = 0f,
                size = u * rnd(0.12f, 0.22f)
            )
        )
    }
}

private fun petalBurst(bits: MutableList<Bit>, geo: PlantGeo) {
    if (!geo.ready) return
    val u = geo.unit
    val now = SystemClock.uptimeMillis()
    repeat(22) {
        val a = rnd(0f, (2 * PI).toFloat())
        val speed = rnd(1.8f, 3.8f) * u
        bits.add(
            Bit(
                geo.flowerX, geo.flowerY,
                vx = cos(a) * speed,
                vy = sin(a) * speed - 1.6f * u,
                born = now + Random.nextLong(120L),
                life = 1700L + Random.nextLong(700L),
                kind = PETAL,
                color = PetalColors[Random.nextInt(PetalColors.size)],
                spin = rnd(-260f, 260f),
                size = u * rnd(0.20f, 0.32f)
            )
        )
    }
    sprinkle(bits, geo, 12, nearFlower = true)
}

private fun DrawScope.drawBits(bits: List<Bit>, now: Long, geo: PlantGeo) {
    if (bits.isEmpty()) return
    for (bit in bits) {
        val tMs = now - bit.born
        if (tMs < 0) continue
        val t = tMs / 1000f
        val k = (tMs.toFloat() / bit.life).coerceIn(0f, 1f)
        val alpha = if (k < 0.15f) k / 0.15f else 1f - (k - 0.15f) / 0.85f
        val grav = if (bit.kind == PETAL) 2.6f * geo.unit else 0f
        val p = Offset(bit.x + bit.vx * t, bit.y + bit.vy * t + 0.5f * grav * t * t)
        if (bit.kind == PETAL) {
            rotate(bit.spin * t, pivot = p) {
                drawOval(
                    bit.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    topLeft = Offset(p.x - bit.size / 2f, p.y - bit.size * 0.3f),
                    size = Size(bit.size, bit.size * 0.6f)
                )
            }
        } else {
            twinkle(p, bit.size * (0.75f + 0.25f * sin(k * PI.toFloat())), bit.color.copy(alpha = alpha.coerceIn(0f, 1f)))
        }
    }
}

/** Four-point sparkle. */
private fun DrawScope.twinkle(c: Offset, s: Float, color: Color) {
    val q = s * 0.22f
    val path = Path().apply {
        moveTo(c.x, c.y - s)
        quadraticBezierTo(c.x + q, c.y - q, c.x + s, c.y)
        quadraticBezierTo(c.x + q, c.y + q, c.x, c.y + s)
        quadraticBezierTo(c.x - q, c.y + q, c.x - s, c.y)
        quadraticBezierTo(c.x - q, c.y - q, c.x, c.y - s)
        close()
    }
    drawPath(path, color)
}
