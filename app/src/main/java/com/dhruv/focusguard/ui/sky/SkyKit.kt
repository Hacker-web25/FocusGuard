package com.dhruv.focusguard.ui.sky

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.dhruv.focusguard.R
import com.dhruv.focusguard.guard.overlay.Backdrop
import com.dhruv.focusguard.guard.overlay.LocalBackdrop
import com.dhruv.focusguard.guard.overlay.LocalRaster
import com.dhruv.focusguard.guard.overlay.LocalScreenSize
import com.dhruv.focusguard.guard.overlay.rememberBackdropRaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

// ═════════════════════════════════════════════════════════════════
//  Units. The design mockups are 1024px wide and drawn for a phone
//  393dp wide, so every measurement taken off them is written here
//  as-is, in mockup pixels: `120.mp` is 120 mockup pixels. Screens
//  sit inside [ScaledToWidth], so other widths keep the proportions.
// ═════════════════════════════════════════════════════════════════

const val MP = 393f / 1024f

/** Mockup pixels → dp. */
val Number.mp: Dp get() = (toFloat() * MP).dp

/** Mockup pixels → sp (font sizes measured off the mockups). */
val Number.ms: TextUnit get() = (toFloat() * MP).sp

/** Layouts are in real dp; only the font scale is capped so huge system text can't break them. */
@Composable
private fun CapFontScale(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    if (d.fontScale <= 1.15f) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides Density(d.density, 1.15f),
            content = content
        )
    }
}

/** Screenshot tests turn the endless animations off so a frame can be captured. */
object SkyMotion {
    @Volatile
    @JvmField
    var enabled = true
}

// ═════════════════════════════════════════════════════════════════
//  Type: the mockups are set in Google Sans (OFL), bundled.
// ═════════════════════════════════════════════════════════════════

val GSans = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_bold, FontWeight.Bold)
)

/** Text at a size measured in mockup pixels, with no hidden font padding. */
fun gs(size: Number, weight: FontWeight = FontWeight.Normal, color: Color = Sky.Navy) = TextStyle(
    fontFamily = GSans,
    fontWeight = weight,
    fontSize = size.ms,
    lineHeight = (size.toFloat() * 1.28f).ms,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

// ═════════════════════════════════════════════════════════════════
//  Palette, sampled from the two mockups.
// ═════════════════════════════════════════════════════════════════

object Sky {
    val Navy = Color(0xFF0B1652)
    val TimerInk = Color(0xFF131A4D)
    val Sub = Color(0xFF7378AB)
    val Muted = Color(0xFF8084B1)
    val Chevron = Color(0xFF8C8FBA)
    val White = Color.White

    // Glass surfaces
    val CardTint = Color(0xFFF1EDF8)          // the big To-do card
    val RowFill = Color(0xFFF5F3F9)           // a task row on that card
    val TopCardTint = Color(0xFFE9E6F8)       // the task card on the focus screen
    val ChipLav = Color(0xFFE7DCFA)           // date chip on the list card
    val ChipLavInk = Color(0xFF5149A9)
    val GlassChip = Color(0xFFDCDDF3)         // date chip / back button over the sky
    val GlassChipInk = Color(0xFF3E459C)

    // Check circle
    val CheckRing = Color(0xFFA2A0C7)
    val CheckDone = Color(0xFF8C86F2)

    // Priority
    val Red = Color(0xFFE95759)
    val RedChip = Color(0xFFFAE1EA)

    // Timer ring
    val ArcFrom = Color(0xFFD2ABFD)
    val ArcTo = Color(0xFFDFA9FA)
    val TrailPink = Color(0xFFF4CAF4)
    val TrackLav = Color(0xFFDDD5FA)
    val Disk = Color(0xFFF8EEF5)
    val FocusLabel = Color(0xFF7278AC)
    val StayInk = Color(0xFF6974A2)
    val Target = Color(0xFF557CD5)

    // Buttons
    val PauseStops = arrayOf(
        0f to Color(0xFFB7B4FD),
        0.28f to Color(0xFF959DFE),
        0.55f to Color(0xFF9A90FB),
        0.82f to Color(0xFFBF9FFC),
        1f to Color(0xFFE4B8F8)
    )
    val ResetInk = Color(0xFF63679D)

    // Audience
    val AudIcon = Color(0xFF7361D7)
    val AudTile = Color(0xFFDED2FB)
    val BarFill = Color(0xFF918EFD)
    val BarTrack = Color(0xFFE6D8EE)
    val Hand = Color(0xFF8584D9)

    // Bubble faces
    val FaceInk = Color(0xFF2E3577)
    val Blush = Color(0xFFF59BB9)
    val Mouth = Color(0xFF3A3F86)
    val Tongue = Color(0xFFF2A3C0)
}

/** A task's colour family: the row spine, icon tile and chip inks. */
@Immutable
data class Tone(
    val spine: Color,
    val tile: Color,
    val icon: Color,
    val chip: Color,
    val dateInk: Color,
    val durationInk: Color
)

object Tones {
    val Red = Tone(Color(0xFFF47F7F), Color(0xFFFAD2D5), Color(0xFFE44F4E), Color(0xFFFAE6EF), Color(0xFFE54352), Color(0xFF081357))
    val Teal = Tone(Color(0xFF78BFBE), Color(0xFFD5EAEB), Color(0xFF3F7F84), Color(0xFFE3EFF3), Color(0xFF458D92), Color(0xFF216B71))
    val Violet = Tone(Color(0xFFA18BFA), Color(0xFFE5DCFC), Color(0xFF5A46C1), Color(0xFFECE7FD), Color(0xFF5449B9), Color(0xFF3C3998))
    val Blue = Tone(Color(0xFF80BAF6), Color(0xFFD9E7F8), Color(0xFF2E5D9B), Color(0xFFE3EDF9), Color(0xFF3A7BB2), Color(0xFF245A97))
    val Amber = Tone(Color(0xFFF5C27D), Color(0xFFFAE4C8), Color(0xFFB46E27), Color(0xFFF8EDE5), Color(0xFFB56F1F), Color(0xFFB2681E))
}

// ═════════════════════════════════════════════════════════════════
//  Absolute placement in mockup pixels. Used inside a full-size Box
//  so each element lands exactly where the mockup has it.
// ═════════════════════════════════════════════════════════════════

/** Top-left corner at mockup (x, y). */
fun Modifier.at(x: Number, y: Number): Modifier = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place(x.mp.roundToPx(), y.mp.roundToPx()) }
}

/**
 * Places the child so that the point (ax * width, ay * height) of it lands on
 * mockup (x, y): (0, 0) is its top-left, (1, 0) its top-right, (0, 0.5) the
 * middle of its left edge.
 */
fun Modifier.anchorAt(x: Number, y: Number, ax: Float, ay: Float): Modifier = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        p.place((x.mp.toPx() - p.width * ax).roundToInt(), (y.mp.toPx() - p.height * ay).roundToInt())
    }
}

/** Centre at mockup (cx, cy). */
fun Modifier.centerAt(cx: Number, cy: Number): Modifier = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        p.place((cx.mp.toPx() - p.width / 2f).roundToInt(), (cy.mp.toPx() - p.height / 2f).roundToInt())
    }
}

/**
 * Text whose first baseline sits at mockup y. [x] is the left edge, or the
 * centre when [centered], or the right edge when [rightAligned].
 */
fun Modifier.baselineAt(x: Number, y: Number, centered: Boolean = false, rightAligned: Boolean = false): Modifier =
    layout { m, c ->
        val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
        val b = p[FirstBaseline].let { if (it == androidx.compose.ui.layout.AlignmentLine.Unspecified) p.height else it }
        val left = when {
            centered -> x.mp.toPx() - p.width / 2f
            rightAligned -> x.mp.toPx() - p.width
            else -> x.mp.toPx()
        }
        layout(p.width, p.height) { p.place(left.roundToInt(), (y.mp.toPx() - b).roundToInt()) }
    }

// ═════════════════════════════════════════════════════════════════
//  The painted sky behind both screens.
// ═════════════════════════════════════════════════════════════════

enum class SkyFit {
    /** Fill the screen, cropping evenly (the focus screen scales with height). */
    Cover,

    /** Match the width and pin to the top (the To-do header never moves). */
    TopWidth
}

/** Decoded once per process. */
private object SkyImages {
    private val cache = HashMap<Int, ImageBitmap>()

    @Synchronized
    fun peek(res: Int): ImageBitmap? = cache[res]

    @Synchronized
    fun get(context: android.content.Context, res: Int): ImageBitmap? {
        cache[res]?.let { return it }
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply { inScaled = false }
            android.graphics.BitmapFactory.decodeResource(context.resources, res, opts)
                ?.asImageBitmap()?.also { cache[res] = it }
        } catch (e: Throwable) {
            null
        }
    }
}

/**
 * Lays the sky painting behind [content] and provides it to the frosted
 * [com.dhruv.focusguard.guard.overlay.Glass] cards, so glass blurs the real
 * painting behind it. Also scales the whole screen to the phone's width.
 */
@Composable
fun SkyScaffold(
    res: Int,
    fit: SkyFit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val image by produceState(
        initialValue = if (com.dhruv.focusguard.guard.overlay.RasterPolicy.synchronous) SkyImages.get(context, res) else SkyImages.peek(res),
        res
    ) {
        if (value == null) value = withContext(Dispatchers.IO) { SkyImages.get(context, res) }
    }
    LightStatusBarIcons()
    CapFontScale {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val screen = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
            val backdrop = remember(image, fit) { SkyBackdrop(image, fit, res) }
            val raster = rememberBackdropRaster(backdrop, screen)
            CompositionLocalProvider(
                LocalBackdrop provides backdrop,
                LocalScreenSize provides screen,
                LocalRaster provides raster
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val sharp = raster?.sharp
                    if (sharp != null) {
                        drawImage(sharp, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium)
                    } else {
                        backdrop.draw(this, size)
                    }
                }
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}

/** The painting, fitted to the screen the way the screen needs it. */
private class SkyBackdrop(
    private val painting: ImageBitmap?,
    private val fit: SkyFit,
    private val res: Int
) : Backdrop(null) {
    override val cacheKey: String get() = "sky:$res:$fit:${painting != null}"

    override fun draw(scope: DrawScope, screen: Size) {
        val img = painting
        if (img == null) {
            scope.drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF7D97DC), Color(0xFFB9B3E6), Color(0xFFF1C8D6), Color(0xFFE5C4E4))
                ),
                size = screen
            )
            return
        }
        val scale = when (fit) {
            SkyFit.Cover -> max(screen.width / img.width, screen.height / img.height)
            SkyFit.TopWidth -> max(screen.width / img.width, screen.height / img.height).let {
                // Width decides; only grow further if the painting would run out at the bottom.
                if (img.height * (screen.width / img.width) >= screen.height) screen.width / img.width else it
            }
        }
        val srcW = (screen.width / scale).toInt().coerceIn(1, img.width)
        val srcH = (screen.height / scale).toInt().coerceIn(1, img.height)
        val srcX = (img.width - srcW) / 2
        val srcY = if (fit == SkyFit.TopWidth) 0 else (img.height - srcH) / 2
        scope.drawImage(
            image = img,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(screen.width.toInt(), screen.height.toInt()),
            filterQuality = FilterQuality.Medium
        )
    }
}

/**
 * White status-bar icons over the sky, restored when the last sky screen
 * leaves. Counted, because during a transition the next sky screen arrives
 * before the previous one is disposed.
 */
private object SkyBars {
    var count = 0
    var savedColor: Int? = null
    var savedLight: Boolean? = null
}

@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (SkyBars.count == 0) {
            @Suppress("DEPRECATION")
            SkyBars.savedColor = window?.statusBarColor
            SkyBars.savedLight = controller?.isAppearanceLightStatusBars
        }
        SkyBars.count++
        @Suppress("DEPRECATION")
        window?.statusBarColor = Color.Transparent.toArgb()
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            SkyBars.count = (SkyBars.count - 1).coerceAtLeast(0)
            if (SkyBars.count == 0) {
                val c = SkyBars.savedColor
                val l = SkyBars.savedLight
                @Suppress("DEPRECATION")
                if (c != null) window?.statusBarColor = c
                if (l != null) controller?.isAppearanceLightStatusBars = l
            }
        }
    }
}

/** A soft drop shadow drawn as stacked translucent rounded rects (works without hardware shadows). */
fun DrawScope.softShadow(topLeft: Offset, size: Size, radius: Float, color: Color, spread: Float, dy: Float) {
    val steps = 6
    for (i in steps downTo 1) {
        val g = spread * i / steps
        drawRoundRect(
            color = color.copy(alpha = color.alpha / steps),
            topLeft = Offset(topLeft.x - g, topLeft.y - g + dy),
            size = Size(size.width + 2 * g, size.height + 2 * g),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius + g)
        )
    }
}

/** Mockup pixels → px inside a DrawScope. */
fun DrawScope.mpx(v: Number): Float = v.toFloat() * MP * density
