package com.dhruv.focusguard.guard.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.ui.plant.GoalPlant
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// Text over the sky gets a soft shadow so it stays readable on any background.
val OnSky = BaseText.copy(
    color = Color.White,
    shadow = Shadow(Color(0x55241F4A), Offset(0f, 2f), 8f)
)

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

// ═════════════════════════════════════════════════════════════════
//  Header
// ═════════════════════════════════════════════════════════════════

@Composable
fun Header(name: String, hour: Int, streak: Int, onSettings: () -> Unit) {
    val greeting = when (hour) {
        in 5..11 -> "Good morning,"
        in 12..16 -> "Good afternoon,"
        in 17..21 -> "Good evening,"
        else -> "Still up,"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Eco, contentDescription = null,
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(-20f)
                        .gradientTint(Brush.linearGradient(listOf(Color(0xFFB8F0D8), Color(0xFF4FB79A))))
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(greeting, style = OnSky, fontSize = 14.sp)
                    Text(
                        name.ifBlank { "there" }, style = OnSky, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Small choices today.\nA bigger tomorrow.",
                style = OnSky, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
        StreakPill(streak)
        Spacer(Modifier.width(8.dp))
        Glass(
            Modifier
                .padding(top = 22.dp)
                .size(46.dp)
                .clickable(onClick = onSettings),
            shape = CircleShape,
            tint = Color.White.copy(alpha = 0.72f),
            elevation = 8.dp
        ) {
            Icon(
                Icons.Rounded.Settings, contentDescription = "FocusGuard settings", tint = P.Navy,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun StreakPill(streak: Int) {
    Box {
        Glass(
            Modifier
                .padding(top = 20.dp)
                .width(150.dp)
                .height(52.dp),
            shape = RoundedCornerShape(50),
            tint = Color.White.copy(alpha = 0.75f),
            elevation = 8.dp
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.LocalFireDepartment, contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .gradientTint(Brush.verticalGradient(listOf(P.Flame1, P.Flame2, P.Flame3)))
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text("Day $streak", style = BaseText, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                    Text("Streak", style = BaseText, fontSize = 10.5.sp, color = P.Navy.copy(alpha = 0.8f), lineHeight = 12.sp)
                    Spacer(Modifier.height(3.dp))
                    MilestoneDots(streak, Modifier.fillMaxWidth().height(6.dp))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = P.Faint, modifier = Modifier.size(18.dp))
            }
        }
        FlameMascot(
            Modifier
                .align(Alignment.TopCenter)
                .offset(x = (-4).dp, y = (-2).dp)
                .size(40.dp, 30.dp)
        )
    }
}

/** Dots for streak milestones (3, 7, 14, 30 days) joined by a line that fills. */
@Composable
private fun MilestoneDots(streak: Int, modifier: Modifier) {
    val marks = listOf(3, 7, 14, 30)
    Canvas(modifier) {
        val n = marks.size
        val gap = size.width / (n - 1)
        val cy = size.height / 2f
        val rDot = size.height / 2f
        drawLine(Color(0xFFCFC8D6), Offset(rDot, cy), Offset(size.width - rDot, cy), 1.5.dp.toPx())
        // progress
        var fx = 0f
        for (i in 0 until n) {
            val prev = if (i == 0) 0 else marks[i - 1]
            if (streak >= marks[i]) fx = i * gap
            else {
                val part = ((streak - prev).coerceAtLeast(0)).toFloat() / (marks[i] - prev)
                fx = if (i == 0) 0f else (i - 1) * gap + part * gap
                break
            }
        }
        if (fx > 0f) drawLine(P.Flame2, Offset(rDot, cy), Offset(fx.coerceAtLeast(rDot), cy), 2.dp.toPx(), StrokeCap.Round)
        for (i in 0 until n) {
            val c = Offset((i * gap).coerceIn(rDot, size.width - rDot), cy)
            val reached = streak >= marks[i] || (i == 0 && streak > 0)
            if (reached) drawCircle(P.Flame2, rDot, c)
            else {
                drawCircle(Color.White, rDot, c)
                drawCircle(Color(0xFFBFB6C9), rDot, c, style = Stroke(1.2.dp.toPx()))
            }
        }
    }
}

/** Little flame-drop friend sitting on the streak pill. */
@Composable
private fun FlameMascot(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "mascot")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "mb")
    Canvas(modifier.graphicsLayer { translationY = -bob * 3.dp.toPx() }) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val body = Path().apply {
            moveTo(cx, 0f)
            cubicTo(cx + w * 0.18f, h * 0.22f, cx + w * 0.36f, h * 0.42f, cx + w * 0.32f, h * 0.70f)
            cubicTo(cx + w * 0.28f, h * 0.98f, cx - w * 0.28f, h * 0.98f, cx - w * 0.32f, h * 0.70f)
            cubicTo(cx - w * 0.36f, h * 0.42f, cx - w * 0.18f, h * 0.22f, cx, 0f)
            close()
        }
        drawPath(body, Brush.verticalGradient(listOf(Color(0xFFB9A6FF), Color(0xFFFFB6A0), Color(0xFFFF9A4D))))
        drawPath(body, Color.White.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
        // arms
        drawCircle(Color(0xFFFFB27A), w * 0.07f, Offset(cx - w * 0.36f, h * 0.70f))
        drawCircle(Color(0xFFFFB27A), w * 0.07f, Offset(cx + w * 0.36f, h * 0.70f))
        // face
        val ink = Color(0xFF3A2A4F)
        drawCircle(ink, w * 0.035f, Offset(cx - w * 0.09f, h * 0.60f))
        drawCircle(ink, w * 0.035f, Offset(cx + w * 0.09f, h * 0.60f))
        val smile = Path().apply {
            moveTo(cx - w * 0.06f, h * 0.70f)
            quadraticBezierTo(cx, h * 0.77f, cx + w * 0.06f, h * 0.70f)
        }
        drawPath(smile, ink, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(P.Blush.copy(alpha = 0.7f), w * 0.04f, Offset(cx - w * 0.16f, h * 0.67f))
        drawCircle(P.Blush.copy(alpha = 0.7f), w * 0.04f, Offset(cx + w * 0.16f, h * 0.67f))
        // sparkles
        star(Offset(w * 0.12f, h * 0.22f), w * 0.05f, Color(0xFFFFE08A))
        star(Offset(w * 0.90f, h * 0.30f), w * 0.04f, Color(0xFFFFE08A))
    }
}

// ═════════════════════════════════════════════════════════════════
//  Timer card + bubble
// ═════════════════════════════════════════════════════════════════

@Composable
fun TimerAndBubble(
    name: String,
    appIcon: ImageBitmap?,
    remainingMs: Long,
    totalMs: Long,
    finished: Boolean,
    haptics: Haptics
) {
    val progress = if (totalMs <= 0L) 1f else 1f - (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val secondsLeft = ((remainingMs + 999) / 1000).toInt()
    Row(
        Modifier
            .fillMaxWidth()
            .height(214.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimerCard(
            appIcon = appIcon,
            remainingMs = remainingMs,
            progress = progress,
            finished = finished,
            modifier = Modifier
                .weight(0.57f)
                .fillMaxHeight()
        )
        Box(
            Modifier
                .weight(0.43f)
                .fillMaxHeight()
        ) {
            // Rock + daisies the bubble sits on
            Canvas(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(x = (-8).dp, y = (-6).dp)
                    .size(120.dp, 34.dp)
            ) { rockAndFlowers() }

            GlowBubble(
                diameter = 92.dp,
                haptics = haptics,
                progress = progress,
                secondsLeft = secondsLeft,
                finished = finished,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(x = (-10).dp, y = (-6).dp)
            )

            // "You got this, Name ♥"
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .rotate(-9f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("You got this", style = OnSky.copy(fontFamily = Hand), fontSize = 13.sp, lineHeight = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name.ifBlank { "you" }, style = OnSky.copy(fontFamily = Hand), fontSize = 13.sp, lineHeight = 15.sp, maxLines = 1)
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFFF9EC4), modifier = Modifier.size(11.dp))
                }
                Canvas(Modifier.size(30.dp, 16.dp)) {
                    val p = Path().apply {
                        moveTo(size.width * 0.75f, 0f)
                        quadraticBezierTo(size.width * 0.7f, size.height * 0.9f, size.width * 0.12f, size.height * 0.85f)
                    }
                    drawPath(p, Color.White, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
                    drawLine(Color.White, Offset(size.width * 0.12f, size.height * 0.85f), Offset(size.width * 0.30f, size.height * 0.55f), 1.4.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White, Offset(size.width * 0.12f, size.height * 0.85f), Offset(size.width * 0.32f, size.height * 1.0f), 1.4.dp.toPx(), StrokeCap.Round)
                }
            }

            // "Tap or rub the glow"
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 6.dp, y = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Glass(
                    Modifier.size(42.dp),
                    shape = CircleShape,
                    tint = Color.White.copy(alpha = 0.22f),
                    rim = Color.White.copy(alpha = 0.7f),
                    elevation = 0.dp
                ) {
                    Icon(
                        Icons.Rounded.TouchApp, contentDescription = null, tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.Center)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap or rub\nthe glow", style = OnSky, fontSize = 10.sp, lineHeight = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TimerCard(appIcon: ImageBitmap?, remainingMs: Long, progress: Float, finished: Boolean, modifier: Modifier) {
    val total = ((remainingMs + 999) / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val lastFive = total in 1..5 && !finished

    Glass(
        modifier,
        shape = RoundedCornerShape(28.dp),
        tint = P.GlassCool.copy(alpha = 0.26f),
        rim = Color.White.copy(alpha = 0.8f),
        blur = 30.dp
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(appIcon, 38.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Instagram", style = OnSky, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                    Text(
                        if (finished) "Unlocked" else "Blocked for",
                        style = OnSky, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.78f), lineHeight = 15.sp
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                DigitPair(h, false)
                UnitText("h")
                Spacer(Modifier.width(6.dp))
                DigitPair(m, false)
                UnitText("m")
                Spacer(Modifier.width(6.dp))
                DigitPair(s, true, lastFive)
                UnitText("s", gradient = true, urgent = lastFive)
            }
            Spacer(Modifier.height(10.dp))
            TimerBar(progress)
            Spacer(Modifier.weight(1f))
            Row {
                Column(Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = P.TimerPink, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.height(5.dp))
                    Icon(Icons.Rounded.AutoAwesome, null, tint = P.TimerPink.copy(alpha = 0.7f), modifier = Modifier.size(9.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (finished) "You waited. Now choose\nwhat you really want."
                    else "Scroll later. Your future self\nwill thank you.",
                    style = OnSky, fontSize = 11.5.sp, lineHeight = 15.sp
                )
            }
        }
    }
}

/** Two digits that roll up like a slot counter when they change. */
@Composable
private fun DigitPair(value: Int, gradient: Boolean, urgent: Boolean = false) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (gradient) {
            pulse.snapTo(if (urgent) 1.18f else 1.07f)
            pulse.animateTo(1f, spring(0.35f, 400f))
        }
    }
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (slideInVertically(tween(260)) { -it / 2 } + fadeIn(tween(200))) togetherWith
                (slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(180)))
        },
        label = "digits",
        modifier = Modifier.graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
        }
    ) { v ->
        val txt = "%02d".format(v)
        val style = Tabular.copy(
            fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
            shadow = if (gradient) null else OnSky.shadow
        )
        if (gradient) {
            Text(
                txt, style = style, color = Color.White,
                modifier = Modifier.gradientTint(
                    if (urgent) Brush.horizontalGradient(listOf(Color(0xFFFF6B8B), Color(0xFFFFB3C7)))
                    else Brush.horizontalGradient(listOf(P.TimerLav, P.TimerPink))
                )
            )
        } else {
            Text(txt, style = style, color = Color.White)
        }
    }
}

@Composable
private fun UnitText(u: String, gradient: Boolean = false, urgent: Boolean = false) {
    val style = OnSky.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Text(
        u, style = if (gradient) style.copy(shadow = null) else style,
        modifier = Modifier
            .padding(bottom = 4.dp, start = 1.dp)
            .then(
                if (gradient) Modifier.gradientTint(
                    if (urgent) Brush.horizontalGradient(listOf(Color(0xFFFF6B8B), Color(0xFFFFB3C7)))
                    else Brush.horizontalGradient(listOf(P.TimerLav, P.TimerPink))
                ) else Modifier
            )
    )
}

@Composable
private fun TimerBar(progress: Float) {
    val p by animateFloatAsState(progress, tween(250, easing = LinearEasing), label = "bar")
    val t = rememberInfiniteTransition(label = "barShimmer")
    val sh by t.animateFloat(-0.2f, 1.2f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "sh")
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.45f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(p.coerceIn(0.02f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(P.BarFrom, P.BarTo)))
                .drawBehind {
                    val x = sh * size.width
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            startX = x - size.width * 0.2f, endX = x + size.width * 0.2f
                        )
                    )
                }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rockAndFlowers() {
    val w = size.width
    val h = size.height
    // stone
    val rock = Path().apply {
        moveTo(w * 0.04f, h * 0.70f)
        cubicTo(w * 0.10f, h * 0.18f, w * 0.85f, h * 0.10f, w * 0.97f, h * 0.62f)
        lineTo(w * 0.94f, h)
        lineTo(w * 0.08f, h)
        close()
    }
    drawPath(rock, Brush.verticalGradient(listOf(Color(0xFFD9C8C2), Color(0xFFA48E92), Color(0xFF7E6C77))))
    drawPath(rock, Color.White.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
    // grass tufts
    val grass = Color(0xFF6FAF6A)
    for (i in 0 until 7) {
        val x = w * (0.06f + i * 0.14f)
        drawLine(grass, Offset(x, h * 0.62f), Offset(x - 4f, h * 0.30f), 2f, StrokeCap.Round)
        drawLine(grass, Offset(x + 3f, h * 0.62f), Offset(x + 7f, h * 0.36f), 2f, StrokeCap.Round)
    }
    // daisies
    fun daisy(cx: Float, cy: Float, r: Float) {
        for (k in 0 until 8) {
            val a = k * 0.785f
            drawCircle(Color.White, r * 0.42f, Offset(cx + kotlin.math.cos(a) * r * 0.62f, cy + kotlin.math.sin(a) * r * 0.62f))
        }
        drawCircle(Color(0xFFFFC94A), r * 0.34f, Offset(cx, cy))
    }
    daisy(w * 0.10f, h * 0.42f, 7.dp.toPx())
    daisy(w * 0.84f, h * 0.38f, 8.dp.toPx())
    daisy(w * 0.93f, h * 0.55f, 5.dp.toPx())
}

// ═════════════════════════════════════════════════════════════════
//  Today's priority
// ═════════════════════════════════════════════════════════════════

private enum class Urg { Overdue, Today, Tomorrow, Soon, Week, Later, None }

private fun urgOf(deadline: Long?, now: Long): Urg {
    if (deadline == null) return Urg.None
    val left = deadline - now
    if (left < 0) return Urg.Overdue
    val todayEnd = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }.timeInMillis
    return when {
        deadline <= todayEnd -> Urg.Today
        deadline <= todayEnd + DAY -> Urg.Tomorrow
        left < 3 * DAY -> Urg.Soon
        left < 7 * DAY -> Urg.Week
        else -> Urg.Later
    }
}

private fun daysBetween(a: Long, b: Long): Int = (abs(b - a) / DAY).toInt().coerceAtLeast(1)

private fun heroLine(task: Task, now: Long): String {
    val d = task.deadline
    return when (urgOf(d, now)) {
        Urg.Overdue -> {
            val n = daysBetween(d!!, now)
            "You’re $n day${if (n == 1) "" else "s"} behind. Don’t let it pile up!"
        }
        Urg.Today -> "Due today. Finish it before you scroll."
        Urg.Tomorrow -> "Due tomorrow. Start tonight, sleep easy."
        Urg.Soon, Urg.Week -> "${daysBetween(now, d!!)} days left. Future you will thank you."
        Urg.Later -> task.description.ifBlank { "Small steps now beat a panic later." }
        Urg.None -> task.description.ifBlank {
            val age = ((now - task.createdAt) / DAY).toInt()
            if (age >= 1) "On your list for $age day${if (age == 1) "" else "s"}. Still waiting." else "Start it now, while it’s small."
        }
    }
}

private fun rowLine(task: Task, now: Long): String = task.description.ifBlank {
    when (urgOf(task.deadline, now)) {
        Urg.Overdue -> "Overdue. Get it off your plate."
        Urg.Today -> "Today is the day."
        Urg.Tomorrow -> "Tomorrow comes fast."
        else -> "One small step at a time."
    }
}

private data class ChipStyle(val bg: Color, val ink: Color)

private fun dueChip(deadline: Long?, now: Long): Pair<String, ChipStyle>? {
    deadline ?: return null
    return when (urgOf(deadline, now)) {
        Urg.Overdue -> "Overdue · ${daysBetween(deadline, now)}d" to ChipStyle(P.RedSoft, P.RedInk)
        Urg.Today -> "Due today" to ChipStyle(P.RedSoft, P.RedInk)
        Urg.Tomorrow -> "Due tomorrow" to ChipStyle(P.RedSoft, P.RedInk)
        Urg.Soon, Urg.Week -> "Due in ${daysBetween(now, deadline)}d" to ChipStyle(P.PurpleBg, P.PurpleInk)
        else -> "Due ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(deadline))}" to ChipStyle(P.BlueBg, P.BlueInk)
    }
}

private fun rowChip(deadline: Long?, now: Long): Pair<String, ChipStyle> {
    if (deadline == null) return "No date" to ChipStyle(P.GreyBg, P.GreyInk)
    return when (urgOf(deadline, now)) {
        Urg.Overdue -> "Overdue" to ChipStyle(P.RedSoft, P.RedInk)
        Urg.Today -> {
            val hrs = ((deadline - now) / HOUR).toInt()
            "Today · ${if (hrs >= 1) "${hrs}h left" else "<1h"}" to ChipStyle(P.PurpleBg, P.PurpleInk)
        }
        Urg.Tomorrow -> "Tomorrow" to ChipStyle(P.BlueBg, P.BlueInk)
        Urg.Soon, Urg.Week -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(deadline)) to ChipStyle(P.BlueBg, P.BlueInk)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(deadline)) to ChipStyle(P.GreyBg, P.GreyInk)
    }
}

private fun isUrgent(task: Task, now: Long): Boolean {
    val u = urgOf(task.deadline, now)
    return u == Urg.Overdue || u == Urg.Today || u == Urg.Tomorrow || u == Urg.Soon || task.priority == Priority.URGENT
}

/** Most urgent first: earliest deadline (overdue first), then priority. */
fun byUrgency(tasks: List<Task>): List<Task> = tasks.sortedWith(
    compareBy<Task>({ it.deadline ?: Long.MAX_VALUE }, { -it.priority.weight })
)

@Composable
fun PriorityCard(
    tasks: List<Task>,
    goals: List<Goal>,
    loaded: Boolean,
    now: Long,
    haptics: Haptics,
    onOpenTask: (Task) -> Unit,
    onComplete: (Task) -> Unit,
    onAddTask: () -> Unit
) {
    val hero = tasks.firstOrNull()
    val rest = tasks.drop(1).take(3)
    Glass(
        Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(30.dp),
        tint = P.GlassWarm.copy(alpha = 0.84f)
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Bolt, contentDescription = null,
                    modifier = Modifier
                        .size(26.dp)
                        .gradientTint(Brush.verticalGradient(listOf(Color(0xFFFF6B7F), P.Lightning)))
                )
                Spacer(Modifier.width(6.dp))
                Text("Today’s priority", style = BaseText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (hero != null && isUrgent(hero, now)) {
                    UrgentBadge(if (urgOf(hero.deadline, now) == Urg.Overdue) "OVERDUE" else "URGENT")
                    Spacer(Modifier.width(22.dp))
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                !loaded -> Box(Modifier.fillMaxWidth().height(120.dp))
                hero == null -> EmptyPriority(onAddTask)
                else -> {
                    HeroRow(hero, goals, now, haptics, onOpen = { onOpenTask(hero) }, onComplete = { onComplete(hero) })
                    rest.forEachIndexed { i, t ->
                        TaskRow(t, now, haptics, onOpen = { onOpenTask(t) }, onComplete = { onComplete(t) })
                        if (i != rest.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp)
                                    .height(1.dp)
                                    .background(P.Divider)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UrgentBadge(label: String) {
    val t = rememberInfiniteTransition(label = "urgent")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "up")
    Box(
        Modifier
            .graphicsLayer {
                val s = 1f + pulse * 0.06f
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                // Alarm rays either side
                val cy = size.height / 2f
                val c = P.Red.copy(alpha = 0.55f + 0.45f * pulse)
                val gap = 5.dp.toPx() + pulse * 2.dp.toPx()
                val len = 7.dp.toPx()
                for (deg in listOf(-30f, 0f, 30f)) {
                    val rad = Math.toRadians(deg.toDouble())
                    val dx = kotlin.math.cos(rad).toFloat()
                    val dy = kotlin.math.sin(rad).toFloat()
                    drawLine(c, Offset(-gap * dx, cy - gap * dy), Offset(-(gap + len) * dx, cy - (gap + len) * dy), 1.8.dp.toPx(), StrokeCap.Round)
                    drawLine(c, Offset(size.width + gap * dx, cy - gap * dy), Offset(size.width + (gap + len) * dx, cy - (gap + len) * dy), 1.8.dp.toPx(), StrokeCap.Round)
                }
            }
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0xFFF0566F), P.Red)))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(label, style = BaseText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroRow(
    task: Task,
    goals: List<Goal>,
    now: Long,
    haptics: Haptics,
    onOpen: () -> Unit,
    onComplete: () -> Unit
) {
    var done by remember(task.id) { mutableStateOf(false) }
    val span = task.deadline?.let { (it - task.createdAt).coerceAtLeast(1L) }
    val used = if (task.deadline != null && span != null) ((now - task.createdAt).toFloat() / span).coerceIn(0.12f, 1f) else 0.35f
    val goal = task.goalId?.let { id -> goals.firstOrNull { it.id == id } }
    val due = dueChip(task.deadline, now)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(P.HeroRow.copy(alpha = 0.95f), P.HeroRow.copy(alpha = 0.55f))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.click(); onOpen()
            }
            .padding(start = 12.dp, end = 6.dp, top = 14.dp, bottom = 14.dp)
            .graphicsLayer { alpha = if (done) 0.45f else 1f }
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CheckRing(
                size = 30.dp, done = done, urgentFraction = used, urgent = true,
                onCheck = {
                    if (!done) {
                        done = true
                        haptics.success()
                        onComplete()
                    }
                }
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title, style = BaseText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(3.dp))
                Text(heroLine(task, now), style = BaseText, fontSize = 13.5.sp, color = P.Sub, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (goal != null) Chip(goal.title.take(16), ChipStyle(P.StudyBg, P.StudyInk), null)
                    if (due != null) Chip(due.first, due.second, Icons.Rounded.CalendarToday)
                    if (task.priority == Priority.HIGH || task.priority == Priority.URGENT) {
                        Chip(
                            if (task.priority == Priority.URGENT) "Urgent" else "High priority",
                            ChipStyle(Color(0xFFFFD9DE), Color(0xFFE3173A)),
                            Icons.Rounded.LocalFireDepartment
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Start this",
                tint = P.Faint, modifier = Modifier
                    .padding(top = 14.dp)
                    .size(24.dp)
            )
        }
    }
}

@Composable
private fun TaskRow(task: Task, now: Long, haptics: Haptics, onOpen: () -> Unit, onComplete: () -> Unit) {
    var done by remember(task.id) { mutableStateOf(false) }
    val chip = rowChip(task.deadline, now)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.click(); onOpen()
            }
            .padding(start = 12.dp, end = 6.dp, top = 13.dp, bottom = 13.dp)
            .graphicsLayer { alpha = if (done) 0.45f else 1f },
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckRing(
            size = 28.dp, done = done, urgentFraction = 0f, urgent = false,
            onCheck = {
                if (!done) {
                    done = true
                    haptics.success()
                    onComplete()
                }
            }
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title, style = BaseText, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null
            )
            Text(rowLine(task, now), style = BaseText, fontSize = 12.5.sp, color = P.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Chip(chip.first, chip.second, Icons.Rounded.Schedule, big = true)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = P.Faint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Chip(text: String, style: ChipStyle, icon: androidx.compose.ui.graphics.vector.ImageVector?, big: Boolean = false) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(style.bg)
            .padding(horizontal = if (big) 10.dp else 10.dp, vertical = if (big) 6.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = style.ink, modifier = Modifier.size(if (big) 15.dp else 14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = BaseText, fontSize = if (big) 12.5.sp else 12.sp, color = style.ink, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Circle checkbox. The hero's ring shows how much of the time to its deadline is gone. */
@Composable
private fun CheckRing(size: Dp, done: Boolean, urgentFraction: Float, urgent: Boolean, onCheck: () -> Unit) {
    val fill by animateFloatAsState(if (done) 1f else 0f, spring(0.5f, 300f), label = "check")
    val t = rememberInfiniteTransition(label = "ring")
    val glow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "rg")
    Box(
        Modifier
            .size(size)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCheck),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 2.6.dp.toPx()
            val inset = sw / 2f
            val arc = Size(this.size.width - sw, this.size.height - sw)
            if (urgent && !done) {
                drawCircle(P.Red.copy(alpha = 0.10f + 0.12f * glow), radius = this.size.minDimension / 2f + 2.dp.toPx())
            }
            drawArc(Color(0xFFD6CCD2), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            if (urgent && !done) {
                drawArc(
                    Brush.sweepGradient(listOf(Color(0xFFFF8FA3), P.Red, Color(0xFFFF8FA3))),
                    -90f, 360f * urgentFraction, false, Offset(inset, inset), arc,
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
            if (fill > 0f) {
                drawCircle(Color(0xFF3DB389), radius = this.size.minDimension / 2f * fill)
            }
        }
        if (fill > 0.5f) {
            Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(size * 0.6f))
        }
    }
}

@Composable
private fun EmptyPriority(onAddTask: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(P.HeroRow.copy(alpha = 0.7f))
            .padding(16.dp)
    ) {
        Text("Nothing on your list yet", style = BaseText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Instagram is filling the space your goals should. Write down the one thing you keep putting off.",
            style = BaseText, fontSize = 13.5.sp, color = P.Sub, lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(P.CtaTop, P.CtaBottom)))
                .clickable(onClick = onAddTask)
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text("Add a task", style = BaseText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Long-term goal
// ═════════════════════════════════════════════════════════════════

fun goalEmoji(title: String): String {
    val t = title.lowercase()
    val map = listOf(
        listOf("guitar") to "🎸", listOf("piano", "keyboard") to "🎹",
        listOf("sing", "vocal") to "🎤", listOf("music", "song") to "🎵",
        listOf("gym", "fit", "workout", "muscle", "lift") to "💪", listOf("run", "marathon", "jog") to "🏃",
        listOf("diet", "weight", "eat", "nutrition") to "🥗", listOf("read", "book") to "📚",
        listOf("write", "blog", "journal") to "✍️", listOf("code", "dsa", "leetcode", "program", "dev", "app") to "💻",
        listOf("exam", "study", "learn", "viva", "college", "gate", "cat", "course") to "🎓",
        listOf("money", "save", "invest", "finance") to "💰", listOf("business", "startup", "launch", "agency") to "🚀",
        listOf("travel", "trip") to "✈️", listOf("language", "spanish", "french", "german", "japanese") to "🗣️",
        listOf("draw", "art", "paint", "design") to "🎨", listOf("photo", "camera") to "📷",
        listOf("cook", "bake") to "🍳", listOf("meditat", "yoga", "calm") to "🧘",
        listOf("sleep", "wake") to "😴", listOf("job", "career", "interview", "promotion") to "💼"
    )
    return map.firstOrNull { (keys, _) -> keys.any { t.contains(it) } }?.second ?: "🎯"
}

private fun timeLeftLabel(target: Long?, now: Long): String {
    target ?: return "No target date"
    val left = target - now
    return when {
        left < 0 -> "Past target"
        left > 60 * DAY -> "${left / (30 * DAY)} months left"
        left > 14 * DAY -> "${left / (7 * DAY)} weeks left"
        else -> "${(left / DAY).coerceAtLeast(1)} days left"
    }
}

@Composable
fun GoalCard(goal: Goal, nextStep: String?, now: Long, haptics: Haptics, onOpen: () -> Unit) {
    val emoji = remember(goal.title) { goalEmoji(goal.title) }
    val prog by animateFloatAsState(goal.progress / 100f, tween(900, easing = FastOutSlowInEasing), label = "goal")
    Glass(
        Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.click(); onOpen()
            },
        shape = RoundedCornerShape(28.dp),
        tint = P.GlassWarm.copy(alpha = 0.82f)
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, top = 14.dp, bottom = 12.dp, end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.TrackChanges, contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .gradientTint(Brush.linearGradient(listOf(Color(0xFFB08BFF), P.GoalLabel)))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("LONG-TERM GOAL", style = BaseText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = P.GoalLabel)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 17.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(goal.title, style = BaseText, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(timeLeftLabel(goal.targetDate, now), style = BaseText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P.GoalGreen)
                    Text("  •  ", style = BaseText, fontSize = 13.sp, color = P.Faint)
                    Text(
                        if (goal.progress >= 100) "In full bloom 🌸" else "${goal.progress}% complete",
                        style = BaseText, fontSize = 13.sp,
                        fontWeight = if (goal.progress >= 100) FontWeight.Bold else FontWeight.Normal,
                        color = if (goal.progress >= 100) Color(0xFFD94C86) else P.GoalGrey
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(P.GoalTrack)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(prog.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(P.GoalBarFrom, P.GoalBarTo)))
                    )
                }
                Text(
                    "Next step: ${nextStep ?: "add one small task for this"}",
                    style = BaseText, fontSize = 12.sp, color = Color(0xFF4D4C68), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
            ) {
                Canvas(Modifier.fillMaxSize()) { goalScene() }
                // The goal's plant: grows as the goal moves, blooms at 100%.
                GoalPlant(
                    progress = goal.progress,
                    haptics = haptics,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, top = 4.dp)
                )
                // Blend the scene into the card on its left edge
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(40.dp)
                        .background(Brush.horizontalGradient(listOf(P.GlassWarm.copy(alpha = 0.95f), Color.Transparent)))
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 10.dp)
                        .size(24.dp)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.goalScene() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFC9B9F0), Color(0xFFF4CDD8), Color(0xFFFBE2CC))))
    drawCircle(
        Brush.radialGradient(listOf(Color(0xCCFFF3DA), Color.Transparent), center = Offset(w * 0.7f, h * 0.35f), radius = w * 0.6f),
        radius = w * 0.6f, center = Offset(w * 0.7f, h * 0.35f)
    )
    val far = Path().apply {
        moveTo(0f, h * 0.62f)
        lineTo(w * 0.22f, h * 0.30f); lineTo(w * 0.40f, h * 0.48f); lineTo(w * 0.58f, h * 0.20f)
        lineTo(w * 0.80f, h * 0.44f); lineTo(w, h * 0.30f); lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(far, Color(0xFFA996D6).copy(alpha = 0.85f))
    val near = Path().apply {
        moveTo(0f, h * 0.80f)
        cubicTo(w * 0.3f, h * 0.62f, w * 0.7f, h * 0.70f, w, h * 0.66f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(near, Brush.verticalGradient(listOf(Color(0xFF9CCB8C), Color(0xFF6E9E6A)), startY = h * 0.62f, endY = h))
    for (i in 0 until 6) {
        val x = w * (0.12f + i * 0.16f)
        val y = h * (0.86f + (i % 2) * 0.05f)
        drawCircle(if (i % 2 == 0) Color(0xFFFFB6D2) else Color.White, 2.6.dp.toPx(), Offset(x, y))
    }
}

// ═════════════════════════════════════════════════════════════════
//  Need a reset?
// ═════════════════════════════════════════════════════════════════

@Composable
fun ResetBar(
    haptics: Haptics,
    onCalmOrb: () -> Unit,
    onBreathe: () -> Unit,
    onTinyWin: () -> Unit,
    onJustBe: () -> Unit
) {
    Glass(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tint = P.GlassWarm.copy(alpha = 0.80f)
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(70.dp)) {
                Text("Need\na reset?", style = BaseText, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
                Spacer(Modifier.height(6.dp))
                Text("Choose what\nfeels right.", style = BaseText, fontSize = 11.sp, color = P.Sub, lineHeight = 14.sp)
            }
            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(72.dp)
                    .background(P.Divider)
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ResetTile("Calm Orb", "Touch & unwind", P.OrbBg, Color(0xFFB98CFF), P.OrbInk, P.OrbSub, haptics, onCalmOrb, Modifier.weight(1f)) {
                    Canvas(Modifier.size(34.dp)) {
                        drawCircle(Brush.radialGradient(listOf(Color(0x66C9A8FF), Color.Transparent)), radius = size.minDimension / 2f)
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color.White, Color(0xFFD7B8FF), Color(0xFF9A6BFF), Color(0xFFFF9ED8)),
                                center = Offset(size.width * 0.38f, size.height * 0.35f), radius = size.minDimension * 0.55f
                            ),
                            radius = size.minDimension * 0.36f
                        )
                        drawCircle(Color.White.copy(alpha = 0.8f), radius = size.minDimension * 0.07f, center = Offset(size.width * 0.38f, size.height * 0.36f))
                    }
                }
                ResetTile("Breathe", "1 min", P.BreatheBg, Color(0xFF7FC4F0), P.BreatheInk, P.BreatheSub, haptics, onBreathe, Modifier.weight(1f)) {
                    TileIcon(Brush.linearGradient(listOf(Color(0xFF7FC4F0), Color(0xFF3E8FC4)))) { m ->
                        Icon(Icons.Rounded.Air, contentDescription = null, modifier = m)
                    }
                }
                ResetTile("Tiny Win", "Do a small task", P.TinyBg, Color(0xFFFFB86B), P.TinyInk, P.TinySub, haptics, onTinyWin, Modifier.weight(1f)) {
                    TileIcon(Brush.linearGradient(listOf(Color(0xFFFFC85A), Color(0xFFFF8A2B)))) { m ->
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = m)
                    }
                }
                ResetTile("Just Be", "No pressure", P.JustBg, Color(0xFF7FD3B5), P.JustInk, P.JustSub, haptics, onJustBe, Modifier.weight(1f)) {
                    Canvas(Modifier.size(30.dp)) { sprout() }
                }
            }
        }
    }
}

@Composable
private fun TileIcon(brush: Brush, icon: @Composable (Modifier) -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        icon(Modifier.size(22.dp).gradientTint(brush))
    }
}

@Composable
private fun ResetTile(
    title: String,
    sub: String,
    bg: Color,
    liquid: Color,
    ink: Color,
    subInk: Color,
    haptics: Haptics,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: @Composable () -> Unit
) {
    LiquidTile(
        background = bg,
        liquid = liquid,
        haptics = haptics,
        onClick = onClick,
        modifier = modifier.height(108.dp),
        radius = 20.dp
    ) {
        icon()
        Spacer(Modifier.height(7.dp))
        Text(title, style = BaseText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text(sub, style = BaseText, fontSize = 9.5.sp, color = subInk, lineHeight = 12.sp, maxLines = 2, textAlign = TextAlign.Center)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.sprout() {
    val w = size.width
    val h = size.height
    val green = Brush.linearGradient(listOf(Color(0xFF7FD3A0), Color(0xFF2F9E7E)))
    drawLine(Color(0xFF2F9E7E), Offset(w / 2f, h * 0.95f), Offset(w / 2f, h * 0.45f), 2.2.dp.toPx(), StrokeCap.Round)
    val left = Path().apply {
        moveTo(w / 2f, h * 0.55f)
        cubicTo(w * 0.15f, h * 0.55f, w * 0.05f, h * 0.20f, w * 0.12f, h * 0.12f)
        cubicTo(w * 0.40f, h * 0.12f, w * 0.52f, h * 0.35f, w / 2f, h * 0.55f)
        close()
    }
    val right = Path().apply {
        moveTo(w / 2f, h * 0.48f)
        cubicTo(w * 0.85f, h * 0.48f, w * 0.95f, h * 0.10f, w * 0.88f, h * 0.02f)
        cubicTo(w * 0.60f, h * 0.02f, w * 0.48f, h * 0.28f, w / 2f, h * 0.48f)
        close()
    }
    drawPath(left, green)
    drawPath(right, green)
}

// ═════════════════════════════════════════════════════════════════
//  Bottom: "You're in control" + liquid CTA + walk-away link
// ═════════════════════════════════════════════════════════════════

@Composable
fun BottomActions(
    unlocked: Boolean,
    remainingLabel: String,
    appIcon: ImageBitmap?,
    haptics: Haptics,
    onContinue: () -> Unit,
    onWalkAway: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The hero: walking away is the big, glowing, one-tap choice.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(62.dp), contentAlignment = Alignment.Center) {
                Text(
                    "You\u2019re\nin control",
                    style = OnSky.copy(fontFamily = Hand), fontSize = 12.sp, lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.rotate(-12f)
                )
            }
            LiquidPill(
                label = "Close Instagram",
                style = PillStyle.Primary,
                hold = false,
                enabled = true,
                haptics = haptics,
                onActivate = onWalkAway,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp, end = 26.dp),
                leading = {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFB8F5DF), Color(0xFF5FCFAE)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Eco, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                },
                trailing = {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        // The quiet option: small, frosted, and it takes a deliberate hold.
        LiquidPill(
            label = if (unlocked) "Hold to open Instagram" else "Open Instagram",
            style = PillStyle.Ghost,
            hold = true,
            enabled = unlocked,
            haptics = haptics,
            onActivate = onContinue,
            lockedLabel = "Unlocks in $remainingLabel",
            modifier = Modifier.fillMaxWidth(0.62f),
            leading = { AppIcon(appIcon, 28.dp) },
            trailing = { if (!unlocked) LockBadge(remainingLabel) }
        )
    }
}

/** Keep a value in sync with real time while composed (ticks every second). */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}
