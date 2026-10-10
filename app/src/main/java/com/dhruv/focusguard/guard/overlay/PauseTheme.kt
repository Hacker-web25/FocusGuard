package com.dhruv.focusguard.guard.overlay

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════
//  Palette: sampled from the mockup
// ═════════════════════════════════════════════════════════════════

object P {
    val Navy = Color(0xFF1C1A3A)
    val Sub = Color(0xFF6B6383)
    val Faint = Color(0xFF9A93AE)
    val White = Color.White

    // Glass
    val GlassWarm = Color(0xFFFBEEE8)          // priority / goal / reset cards
    val GlassCool = Color(0xFFE9E6FF)          // timer card tint
    val HeroRow = Color(0xFFF9DCD6)
    val Divider = Color(0xFFEBDAD3)
    val Shadow = Color(0xFF5A4A8A)

    // Urgency + chips
    val Red = Color(0xFFE2304F)
    val RedSoft = Color(0xFFFFDCE2)
    val RedInk = Color(0xFFD21A45)
    val Lightning = Color(0xFFE63551)
    val StudyBg = Color(0xFFE6D5FB)
    val StudyInk = Color(0xFF6B3FD0)
    val BlueBg = Color(0xFFD9E7F6)
    val BlueInk = Color(0xFF2378BE)
    val PurpleBg = Color(0xFFEADDF5)
    val PurpleInk = Color(0xFF7647C2)
    val GreyBg = Color(0xFFECE6EA)
    val GreyInk = Color(0xFF6E6880)

    // Timer
    val TimerLav = Color(0xFFB79CFF)
    val TimerPink = Color(0xFFF7C3F2)
    val BarFrom = Color(0xFFB08BFF)
    val BarTo = Color(0xFFE6B8FF)

    // Goal
    val GoalLabel = Color(0xFF7154CB)
    val GoalGreen = Color(0xFF2A9A86)
    val GoalGrey = Color(0xFF5C7286)
    val GoalBarFrom = Color(0xFF3DB389)
    val GoalBarTo = Color(0xFF6BD3A6)
    val GoalTrack = Color(0xFFE6DCE0)

    // Reset tiles
    val OrbBg = Color(0xFFEADCFB); val OrbInk = Color(0xFF2A2A57); val OrbSub = Color(0xFF6A64A3)
    val BreatheBg = Color(0xFFDEE7EF); val BreatheInk = Color(0xFF0D4A6B); val BreatheSub = Color(0xFF74859A)
    val TinyBg = Color(0xFFFBE7D5); val TinyInk = Color(0xFF1A1A2E); val TinySub = Color(0xFF85767A)
    val JustBg = Color(0xFFE0EFE9); val JustInk = Color(0xFF0B6B63); val JustSub = Color(0xFF6E808A)

    // CTA
    val CtaTop = Color(0xFF5A57A8)
    val CtaMid = Color(0xFF45449A)
    val CtaBottom = Color(0xFF6268BD)
    val CtaGlow = Color(0xFF6941EA)
    val CtaRim = Color(0xFF9FC2FF)
    val LiquidFrom = Color(0xFF8E6BFF)
    val LiquidMid = Color(0xFFC78BFF)
    val LiquidTo = Color(0xFFFF9ED8)

    // Streak
    val Flame1 = Color(0xFFFFB13D)
    val Flame2 = Color(0xFFFF6A2B)
    val Flame3 = Color(0xFFE8452C)

    // Bubble
    val BubbleCool1 = Color(0xFF9FC1FE)
    val BubbleCool2 = Color(0xFFCBB8FF)
    val BubbleWarm1 = Color(0xFFF9C6E8)
    val BubbleWarm2 = Color(0xFFFFD6B8)
    val Blush = Color(0xFFF4A3C0)
    val Petal = Color(0xFFF6B6CF)
}

// ═════════════════════════════════════════════════════════════════
//  Type
// ═════════════════════════════════════════════════════════════════

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)

/** Casual handwriting for the little notes ("You got this", "You're in control"). */
val Hand = FontFamily(Font(R.font.gloria_hallelujah, FontWeight.Normal))

val BaseText = TextStyle(fontFamily = Inter, color = P.Navy)
val Tabular = TextStyle(fontFamily = Inter, fontFeatureSettings = "tnum")

// ═════════════════════════════════════════════════════════════════
//  Scale: the layout is designed for a 393dp-wide phone and scaled
//  uniformly to the real width, so proportions match the mockup on
//  every screen. Font scale is capped so large system text can't
//  break the composition.
// ═════════════════════════════════════════════════════════════════

@Composable
fun ScaledToWidth(content: @Composable () -> Unit) {
    val conf = LocalConfiguration.current
    val d = LocalDensity.current
    val s = (conf.screenWidthDp / 393f).coerceIn(0.82f, 1.3f)
    CompositionLocalProvider(
        LocalDensity provides Density(d.density * s, d.fontScale.coerceAtMost(1.1f)),
        content = content
    )
}

// ═════════════════════════════════════════════════════════════════
//  Backdrop: your painting if present, otherwise a code-drawn sky.
//
//  To use your own: put the image at
//    app/src/main/res/drawable-nodpi/pause_bg.jpg   (or .png / .webp)
//  It is picked up by name at runtime, so no code change is needed.
// ═════════════════════════════════════════════════════════════════

@Stable
class Backdrop(val image: ImageBitmap?) {
    fun draw(scope: DrawScope, screen: Size) {
        if (image != null) scope.drawCover(image, screen) else scope.drawSky(screen)
    }
}

val LocalBackdrop = staticCompositionLocalOf { Backdrop(null) }
val LocalScreenSize = staticCompositionLocalOf { Size(1080f, 2400f) }

@Composable
fun rememberBackdrop(): Backdrop {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadPauseBackground(context) }
    }
    return remember(image) { Backdrop(image) }
}

private fun loadPauseBackground(context: Context): ImageBitmap? {
    val id = context.resources.getIdentifier("pause_bg", "drawable", context.packageName)
    if (id == 0) return null
    return try {
        val metrics = context.resources.displayMetrics
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, id, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= metrics.widthPixels &&
            bounds.outHeight / (sample * 2) >= metrics.heightPixels
        ) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeResource(context.resources, id, opts)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/** Centre-crop an image to fill the screen. */
private fun DrawScope.drawCover(image: ImageBitmap, screen: Size) {
    val scale = max(screen.width / image.width, screen.height / image.height)
    val srcW = (screen.width / scale).toInt().coerceAtMost(image.width)
    val srcH = (screen.height / scale).toInt().coerceAtMost(image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(screen.width.toInt(), screen.height.toInt()),
        filterQuality = FilterQuality.Medium
    )
}

/** Dusk sky in the mockup's palette: blue top, lavender, peach glow, hills. */
private fun DrawScope.drawSky(screen: Size) {
    val w = screen.width
    val h = screen.height
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF4C78BD),
            0.22f to Color(0xFF7F9AD6),
            0.42f to Color(0xFFBDB7E2),
            0.58f to Color(0xFFEBC8D2),
            0.72f to Color(0xFFF6CDBF),
            1f to Color(0xFFE7B9C6),
            endY = h
        ),
        size = screen
    )
    // Sun glow behind the bubble
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xCCFFF1D6), Color(0x55FFD9C2), Color.Transparent),
            center = Offset(w * 0.68f, h * 0.24f), radius = w * 0.75f
        ),
        radius = w * 0.75f, center = Offset(w * 0.68f, h * 0.24f)
    )
    // Clouds
    fun cloud(cx: Float, cy: Float, s: Float, a: Float) {
        val c = Color.White.copy(alpha = a)
        for ((dx, dy, r) in listOf(Triple(0f, 0f, 1f), Triple(-0.9f, 0.25f, 0.7f), Triple(0.95f, 0.2f, 0.75f), Triple(0.4f, -0.35f, 0.65f))) {
            drawCircle(
                Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(cx + dx * s, cy + dy * s), radius = r * s * 1.4f),
                radius = r * s * 1.4f, center = Offset(cx + dx * s, cy + dy * s)
            )
        }
    }
    cloud(w * 0.42f, h * 0.06f, w * 0.10f, 0.35f)
    cloud(w * 0.88f, h * 0.15f, w * 0.12f, 0.30f)
    cloud(w * 0.15f, h * 0.30f, w * 0.13f, 0.28f)

    // Distant mountains
    fun ridge(baseY: Float, amp: Float, freq: Float, phase: Float, color: Color) {
        val p = Path().apply {
            moveTo(0f, h)
            lineTo(0f, baseY)
            var x = 0f
            val step = w / 48f
            while (x <= w + step) {
                val y = baseY - amp * (0.55f + 0.45f * sin(x / w * freq + phase)) *
                    (0.7f + 0.3f * sin(x / w * freq * 2.7f + phase * 1.3f))
                lineTo(x, y)
                x += step
            }
            lineTo(w, h)
            close()
        }
        drawPath(p, color)
    }
    ridge(h * 0.50f, h * 0.10f, 7f, 0.6f, Color(0x669A8CCB))
    ridge(h * 0.60f, h * 0.08f, 5f, 2.1f, Color(0x7F8E86BF))
    ridge(h * 0.74f, h * 0.06f, 4f, 4.0f, Color(0x996F8A93))
    // Meadow
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0x00A9C59A), Color(0xAA8FB08A), Color(0xFF6E8E74)),
            startY = h * 0.78f, endY = h
        ),
        topLeft = Offset(0f, h * 0.78f),
        size = Size(w, h * 0.22f)
    )
}

/** Full-screen backdrop. */
@Composable
fun BackdropLayer() {
    val backdrop = LocalBackdrop.current
    val screen = LocalScreenSize.current
    Canvas(Modifier.fillMaxSize()) { backdrop.draw(this, screen) }
}

/** A few pink petals drifting down, like the mockup. Purely decorative. */
@Composable
fun FallingPetals() {
    val t = rememberInfiniteTransition(label = "petals")
    val time by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Restart),
        label = "petalTime"
    )
    val seeds = remember {
        List(9) { i ->
            floatArrayOf(
                ((i * 0.618f) % 1f),            // x start
                ((i * 0.377f + 0.1f) % 1f),     // y phase
                0.7f + (i % 3) * 0.25f,         // size
                1f + (i % 4) * 0.35f,           // sway freq
                (i * 47f) % 360f                // rotation
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val unit = 9.dp.toPx()
        for (s in seeds) {
            val p = (time + s[1]) % 1f
            val y = -0.05f * h + p * h * 1.1f
            val x = (s[0] * w + p * w * 0.25f + sin(p * 6.28f * s[3]) * w * 0.04f) % w
            val r = unit * s[2]
            rotate(s[4] + p * 540f, pivot = Offset(x, y)) {
                val petal = Path().apply {
                    moveTo(x, y - r)
                    quadraticBezierTo(x + r * 0.9f, y - r * 0.2f, x, y + r)
                    quadraticBezierTo(x - r * 0.9f, y - r * 0.2f, x, y - r)
                    close()
                }
                drawPath(petal, Brush.linearGradient(listOf(Color(0xFFFFD6E6), P.Petal), start = Offset(x, y - r), end = Offset(x, y + r)), alpha = 0.85f)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Frosted glass: blurs the backdrop behind the card (Android 12+),
//  then lays a tint and a bright rim on top, like iOS glass.
// ═════════════════════════════════════════════════════════════════

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = P.GlassWarm.copy(alpha = 0.80f),
    blur: Dp = 26.dp,
    rim: Color = Color.White.copy(alpha = 0.85f),
    elevation: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current
    val screen = LocalScreenSize.current
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .shadow(elevation, shape, clip = false, ambientColor = P.Shadow.copy(alpha = 0.25f), spotColor = P.Shadow.copy(alpha = 0.35f))
            .clip(shape)
            .onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .blur(blur, BlurredEdgeTreatment.Rectangle)
            ) {
                translate(-origin.x, -origin.y) { backdrop.draw(this, screen) }
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(tint)
        )
        Box(
            Modifier
                .matchParentSize()
                .border(
                    1.2.dp,
                    Brush.verticalGradient(listOf(rim, rim.copy(alpha = rim.alpha * 0.25f))),
                    shape
                )
        )
        content()
    }
}

/** Paints any content (text, icons) with a gradient. */
fun Modifier.gradientTint(brush: Brush): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.SrcIn)
    }
