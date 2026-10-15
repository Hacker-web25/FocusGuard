package com.dhruv.focusguard.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.ui.theme.Radius
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import kotlinx.coroutines.delay

private const val SPRINT_MS = 5 * 60_000L

/**
 * A five-minute sprint on one task, the in-app twin of the pause screen's
 * Tiny Win. The ring empties, the last ten seconds tick under your finger,
 * and finishing it marks the task done.
 */
@Composable
fun FocusSprintDialog(
    task: Task,
    haptics: Haptics?,
    onFinished: (minutes: Int, completed: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val c = MaterialTheme.mindful
    val start = remember { SystemClock.elapsedRealtime() }
    // Ticks ten times a second for the ring, which reads it while drawing;
    // the clock text derives from it and so changes once a second.
    val left = remember { mutableLongStateOf(SPRINT_MS) }
    val seconds by remember { derivedStateOf { (left.longValue + 999) / 1000 } }
    val over = seconds == 0L

    LaunchedEffect(Unit) {
        haptics?.thud()
        var lastWhole = -1L
        while (true) {
            val remaining = (SPRINT_MS - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0L)
            left.longValue = remaining
            val whole = (remaining + 999) / 1000
            if (whole != lastWhole) {
                if (lastWhole != -1L && whole in 1L..10L) haptics?.tick()
                lastWhole = whole
            }
            if (remaining == 0L) {
                haptics?.success()
                break
            }
            delay(100)
        }
    }

    val minutesSpent = (((SPRINT_MS - left.longValue) + 30_000) / 60_000).toInt().coerceAtLeast(1)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pop = remember { Animatable(0.9f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f)) }

        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(Radius.cardLarge)
                .background(c.surface)
                .padding(Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (over) "TIME'S UP" else "FOCUS SPRINT",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.Bold,
                color = c.accentDeep
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                task.title,
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.lg))

            SprintRing(fraction = { 1f - left.longValue.toFloat() / SPRINT_MS }, urgent = seconds in 1..10) {
                Text(
                    "%d:%02d".format(seconds / 60, seconds % 60),
                    style = MaterialTheme.typography.displaySmall,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    color = c.textPrimary
                )
            }

            Spacer(Modifier.height(Space.lg))
            Text(
                if (over) "Five minutes in. The hardest part is behind you."
                else "Phone down. Just start — five minutes is enough to break the wall.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.md))
            PrimaryButton(
                text = "It's done",
                onClick = { onFinished(minutesSpent, true) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.xs))
            SecondaryButton(
                text = if (over) "Keep going without the timer" else "Stop",
                onClick = { onFinished(minutesSpent, false) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The sprint ring. [fraction] is read inside the draw lambda, so the ring
 * repaints every tick without recomposing anything around it.
 */
@Composable
private fun SprintRing(
    fraction: () -> Float,
    urgent: Boolean,
    content: @Composable () -> Unit
) {
    val c = MaterialTheme.mindful
    val t = rememberInfiniteTransition(label = "sprint")
    val pulse = t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sprintPulse"
    )
    val spin = t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "sprintSpin"
    )
    val track = c.surfaceSunken
    val arcFrom = c.accent
    val arcTo = c.accentDeep
    val hot = c.danger

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(196.dp)) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2f
            val rect = Size(size.width - stroke, size.height - stroke)
            if (urgent) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(hot.copy(alpha = 0.18f * pulse.value), Color.Transparent),
                        center = center, radius = size.minDimension * 0.55f
                    ),
                    radius = size.minDimension * 0.55f
                )
            }
            drawArc(track, 0f, 360f, false, Offset(inset, inset), rect, style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(arcFrom, arcTo, arcFrom), center = center),
                -90f + spin.value * 0.03f, 360f * fraction().coerceIn(0f, 1f), false,
                Offset(inset, inset), rect, style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/** A row of the sprint's own stats, used under the ring on wider cards. */
@Composable
fun SprintStat(value: String, label: String) {
    val c = MaterialTheme.mindful
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}
