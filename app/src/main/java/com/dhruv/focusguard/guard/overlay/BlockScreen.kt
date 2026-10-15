package com.dhruv.focusguard.guard.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.GuardEngine
import com.dhruv.focusguard.guard.Haptics
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═════════════════════════════════════════════════════════════════
//  "Not right now."
//
//  Shown instead of the pause when a hard limit is in force: the daily
//  budget is spent, or a long unbroken scroll has earned a cool-down.
//  There is no countdown to wait out and no Continue button — the only
//  way on is to close Instagram, so the screen gives you somewhere
//  better to go instead of just saying no.
// ═════════════════════════════════════════════════════════════════

@Composable
fun BlockedScreen(
    state: PauseState,
    haptics: Haptics,
    onStartTask: (Task?) -> Unit,
    onBreathe: () -> Unit,
    onClose: () -> Unit
) {
    val cooldown = state.blockReason == GuardEngine.BlockReason.Cooldown
    val top = rememberStatusBarHeight()
    val task = remember(state.tasks) { byUrgency(state.tasks).firstOrNull() }

    // One clock for the whole screen, read only by the leaf that shows it.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val leftMs = (state.blockUntilWall - now).coerceAtLeast(0L)

    // When the wait is over, let the user straight back out of the way.
    LaunchedEffect(leftMs == 0L) {
        if (leftMs == 0L && state.blockUntilWall > 0L) haptics.success()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = top + 8.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Appear(0) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (cooldown) Icons.Rounded.HourglassTop else Icons.Rounded.Bedtime,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (cooldown) "COOLING DOWN" else "DAILY LIMIT REACHED",
                    style = OnSky, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Appear(1) {
            Text(
                if (cooldown) "Take five." else "That’s your Instagram for today.",
                style = OnSky, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold,
                lineHeight = 36.sp, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
        Appear(2) {
            Text(
                if (cooldown) {
                    "You’ve been scrolling a while, ${state.userName.ifBlank { "friend" }}. " +
                        "Instagram comes back on its own — nothing to tap."
                } else {
                    "You set the limit yourself, and you’ve used it. " +
                        "It opens again tomorrow, fresh."
                },
                style = OnSky, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))

        Appear(3) {
            CountdownDial(
                cooldown = cooldown,
                leftMs = leftMs,
                totalMs = state.blockTotalMs,
                untilWall = state.blockUntilWall,
                usedMinutes = state.usedMinutes,
                limitMinutes = state.limitMinutes
            )
        }

        Spacer(Modifier.height(16.dp))

        Appear(4) {
            Glass(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                tint = P.GlassWarm.copy(alpha = 0.88f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "WHILE YOU’RE HERE", style = BaseText, fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = P.GoalLabel
                    )
                    Spacer(Modifier.height(8.dp))
                    if (task != null) {
                        Text(
                            task.title, style = BaseText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Five minutes on this beats five more minutes of reels.",
                            style = BaseText, fontSize = 12.5.sp, color = P.Sub, lineHeight = 17.sp
                        )
                    } else {
                        Text(
                            "Nothing on your list.", style = BaseText, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "A minute of breathing, then back to your day.",
                            style = BaseText, fontSize = 12.5.sp, color = P.Sub, lineHeight = 17.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (task != null) {
                            LiquidTile(
                                background = P.TinyBg,
                                liquid = Color(0xFFFFB86B),
                                haptics = haptics,
                                onClick = { onStartTask(task) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(62.dp),
                                radius = 18.dp
                            ) {
                                Text(
                                    "Start it · 5 min", style = BaseText, fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold, color = P.TinyInk
                                )
                            }
                        }
                        LiquidTile(
                            background = P.BreatheBg,
                            liquid = Color(0xFF7FC4F0),
                            haptics = haptics,
                            onClick = onBreathe,
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp),
                            radius = 18.dp
                        ) {
                            Text(
                                "Breathe · 1 min", style = BaseText, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = P.BreatheInk
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Appear(5) {
            LiquidPill(
                label = "Close Instagram",
                style = PillStyle.Primary,
                hold = false,
                enabled = true,
                haptics = haptics,
                onActivate = onClose,
                modifier = Modifier.fillMaxWidth(),
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
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (state.streak > 0) "🔥  Day ${state.streak} of walking away" else "This is the app doing its job.",
            style = OnSky, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)
        )
    }
}

/**
 * The wait, drawn as a ring. For a cool-down it empties as the minutes pass;
 * for the daily budget it shows how much of the budget went.
 */
@Composable
private fun CountdownDial(
    cooldown: Boolean,
    leftMs: Long,
    totalMs: Long,
    untilWall: Long,
    usedMinutes: Int,
    limitMinutes: Int
) {
    val t = rememberInfiniteTransition(label = "dial")
    // Read in the draw lambda only, so the ring breathes without recomposing.
    val pulse = t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dialPulse"
    )
    val spin = t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "dialSpin"
    )
    val fraction = when {
        cooldown && totalMs > 0 -> (leftMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        !cooldown && limitMinutes > 0 -> 1f
        else -> 1f
    }

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(188.dp)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f + 6.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            val p = pulse.value
            // Soft glow that breathes
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f + 0.10f * p),
                        Color.White.copy(alpha = 0f)
                    ),
                    center = center, radius = size.minDimension * 0.52f
                ),
                radius = size.minDimension * 0.52f
            )
            drawArc(
                Color.White.copy(alpha = 0.22f), 0f, 360f, false,
                Offset(inset, inset), arc, style = Stroke(stroke)
            )
            drawArc(
                Brush.sweepGradient(
                    listOf(Color(0xFFB8F5DF), Color(0xFFCBB8FF), Color(0xFFFFC6DD), Color(0xFFB8F5DF)),
                    center = center
                ),
                -90f + spin.value * 0.05f, -360f * fraction, false,
                Offset(inset, inset), arc,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (cooldown) {
                Text(clockLabel(leftMs), style = Tabular.copy(color = Color.White), fontSize = 46.sp, fontWeight = FontWeight.Light)
                Text("until it opens", style = OnSky, fontSize = 13.sp)
            } else {
                Text("$usedMinutes", style = Tabular.copy(color = Color.White), fontSize = 46.sp, fontWeight = FontWeight.Light)
                Text(
                    if (limitMinutes > 0) "of $limitMinutes min used" else "minutes today",
                    style = OnSky, fontSize = 13.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "back at ${timeOfDay(untilWall)}",
                    style = OnSky, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun timeOfDay(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
