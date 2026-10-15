package com.dhruv.focusguard.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.ui.theme.Radius
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════
//  The task list's own vocabulary: urgency you feel, and ticking
//  something off that actually feels like something.
//
//  Everything here is drawn with paths and springs — no images, and
//  every animated value is read inside a draw lambda so a running
//  animation repaints without recomposing the list.
// ═════════════════════════════════════════════════════════════════

private const val DAY_MS = 24 * 60 * 60 * 1000L

enum class Urgency { Overdue, Today, Tomorrow, Soon, Later, None }

fun urgencyOf(task: Task, now: Long): Urgency {
    val deadline = task.deadline ?: return if (task.priority == Priority.URGENT) Urgency.Today else Urgency.None
    val endOfToday = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }.timeInMillis
    return when {
        deadline < now -> Urgency.Overdue
        deadline <= endOfToday -> Urgency.Today
        deadline <= endOfToday + DAY_MS -> Urgency.Tomorrow
        deadline - now < 7 * DAY_MS -> Urgency.Soon
        else -> Urgency.Later
    }
}

/** Most pressing first: overdue, then by deadline, then by priority. */
fun byUrgency(tasks: List<Task>): List<Task> =
    tasks.sortedWith(compareBy({ it.deadline ?: Long.MAX_VALUE }, { -it.priority.weight }))

@Composable
fun urgencyInk(urgency: Urgency): Color {
    val c = MaterialTheme.mindful
    return when (urgency) {
        Urgency.Overdue, Urgency.Today -> c.danger
        Urgency.Tomorrow -> c.warning
        Urgency.Soon -> c.accentDeep
        else -> c.textSecondary
    }
}

fun urgencyLabel(task: Task, urgency: Urgency, now: Long): String? = when (urgency) {
    Urgency.Overdue -> {
        val days = ((now - (task.deadline ?: now)) / DAY_MS).toInt()
        if (days >= 1) "$days day${if (days == 1) "" else "s"} late" else "Overdue"
    }
    Urgency.Today -> if (task.deadline == null) "Urgent" else "Today"
    Urgency.Tomorrow -> "Tomorrow"
    Urgency.Soon -> task.deadline?.let {
        val days = ((it - now) / DAY_MS).toInt().coerceAtLeast(1)
        "$days day${if (days == 1) "" else "s"} left"
    }
    else -> null
}

/** Small pill that carries the urgency colour. */
@Composable
fun UrgencyChip(label: String, urgency: Urgency, strong: Boolean = false) {
    val c = MaterialTheme.mindful
    val ink = urgencyInk(urgency)
    val hot = urgency == Urgency.Overdue || urgency == Urgency.Today
    Row(
        Modifier
            .clip(Radius.chip)
            .background(if (hot) ink.copy(alpha = if (c.isDark) 0.22f else 0.14f) else c.surfaceSunken)
            .padding(horizontal = if (strong) 11.dp else 9.dp, vertical = if (strong) 5.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hot) {
            Icon(
                if (urgency == Urgency.Overdue) Icons.Rounded.Bolt else Icons.Rounded.Schedule,
                contentDescription = null, tint = ink,
                modifier = Modifier.size(if (strong) 15.dp else 13.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = if (strong) 13.sp else 11.5.sp,
            fontWeight = if (hot) FontWeight.Bold else FontWeight.Medium,
            color = if (hot) ink else c.textSecondary,
            maxLines = 1
        )
    }
}

// ── The check ring ─────────────────────────────────────────────

private class Spark(val angle: Float, val hue: Color, val dist: Float, val size: Float)

/**
 * Tick a task off and it is worth watching: the ring squashes, floods with
 * colour, draws its check in one stroke and throws a ring of sparks.
 */
@Composable
fun CheckRing(
    completed: Boolean,
    accent: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    haptics: Haptics? = null,
    contentDescription: String? = null
) {
    val c = MaterialTheme.mindful
    val scope = rememberCoroutineScope()
    val fill = remember { Animatable(if (completed) 1f else 0f) }
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(if (completed) 1f else 0f) }
    val sparks = remember(accent) {
        List(8) { i ->
            Spark(
                angle = i * 45f + 12f,
                hue = if (i % 2 == 0) accent else Color(0xFFFFC9DD),
                dist = 0.9f + (i % 3) * 0.18f,
                size = 0.12f + (i % 2) * 0.05f
            )
        }
    }

    LaunchedEffect(completed) {
        if (completed) {
            if (fill.value < 1f) {
                launch { burst.snapTo(0f); burst.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }
                launch {
                    pop.animateTo(0.82f, tween(90))
                    pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
                }
            }
            fill.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 320f))
        } else {
            burst.snapTo(0f)
            fill.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier
            .size(size + 16.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClickLabel = contentDescription) {
                haptics?.let { if (!completed) it.success() else it.tick() }
                scope.launch {
                    pop.animateTo(0.86f, tween(70))
                    pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
                }
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        val ringTrack = c.borderStrong
        Canvas(Modifier.size(size)) {
            val f = fill.value
            val b = burst.value
            val p = pop.value
            val r = this.size.minDimension / 2f
            val stroke = r * 0.19f

            // Sparks fly out and fade as the tick lands
            if (b > 0f && b < 1f) {
                val spread = FastOutSlowInEasing.transform(b)
                for (s in sparks) {
                    val rad = Math.toRadians(s.angle.toDouble())
                    val d = r * (1.15f + s.dist * 1.1f * spread)
                    val alpha = (1f - spread) * 0.9f
                    drawCircle(
                        s.hue.copy(alpha = alpha),
                        radius = r * s.size * (1f - spread * 0.4f),
                        center = center + Offset((cos(rad) * d).toFloat(), (sin(rad) * d).toFloat())
                    )
                }
            }

            scaleAround(p) {
                // Empty ring
                drawCircle(ringTrack, radius = r - stroke / 2f, style = Stroke(stroke))
                if (f > 0f) {
                    // Colour floods in from the middle
                    drawCircle(accent, radius = (r - stroke / 2f) * f)
                    // …then the check draws itself
                    val t = ((f - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    if (t > 0f) {
                        val a = center + Offset(-r * 0.36f, r * 0.02f)
                        val mid = center + Offset(-r * 0.09f, r * 0.30f)
                        val end = center + Offset(r * 0.40f, -r * 0.28f)
                        val path = Path().apply {
                            moveTo(a.x, a.y)
                            if (t <= 0.45f) {
                                val k = t / 0.45f
                                lineTo(a.x + (mid.x - a.x) * k, a.y + (mid.y - a.y) * k)
                            } else {
                                lineTo(mid.x, mid.y)
                                val k = (t - 0.45f) / 0.55f
                                lineTo(mid.x + (end.x - mid.x) * k, mid.y + (end.y - mid.y) * k)
                            }
                        }
                        drawPath(
                            path,
                            Color.White,
                            style = Stroke(width = stroke * 1.05f, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

/** Helper so the canvas above can scale around its own centre. */
private inline fun DrawScope.scaleAround(factor: Float, crossinline block: DrawScope.() -> Unit) =
    scale(factor, factor, center) { block() }

// ── The hero card ──────────────────────────────────────────────

/**
 * The one thing to do next, given the room it deserves. Overdue work turns
 * the card's edge red and gives the type real weight, so the list cannot be
 * scrolled past without noticing.
 */
@Composable
fun HeroTaskCard(
    task: Task?,
    now: Long,
    haptics: Haptics?,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onStartFocus: () -> Unit,
    onAdd: () -> Unit
) {
    val c = MaterialTheme.mindful
    val urgency = remember(task, now) { task?.let { urgencyOf(it, now) } ?: Urgency.None }
    val ink = urgencyInk(urgency)
    val hot = urgency == Urgency.Overdue || urgency == Urgency.Today
    val edge by animateColorAsState(
        if (task == null) c.accent else ink,
        tween(400), label = "heroEdge"
    )

    MindfulCard(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.cardLarge,
        contentPadding = 0.dp
    ) {
        Box {
            // Coloured spine down the left edge
            Box(
                Modifier
                    .width(5.dp)
                    .height(if (task == null) 104.dp else 148.dp)
                    .background(
                        Brush.verticalGradient(listOf(edge, edge.copy(alpha = 0.25f)))
                    )
            )
            Column(Modifier.padding(start = Space.md, end = Space.md, top = Space.md, bottom = Space.sm)) {
                Text(
                    if (task == null) "ALL CLEAR" else "UP NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task == null) c.accentDeep else ink
                )
                Spacer(Modifier.height(Space.xs))

                if (task == null) {
                    Text(
                        "Nothing is waiting on you",
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.textPrimary
                    )
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        "Add what matters next, and it'll be the first thing you see when you reach for Instagram.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                    Spacer(Modifier.height(Space.sm))
                    PrimaryButton(text = "Add a task", onClick = onAdd, modifier = Modifier.fillMaxWidth())
                } else {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(Radius.field)
                                .clickable(onClick = onOpen)
                        ) {
                            Text(
                                task.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontSize = if (task.title.length > 42) 21.sp else 25.sp,
                                lineHeight = if (task.title.length > 42) 27.sp else 30.sp,
                                fontWeight = if (hot) FontWeight.ExtraBold else FontWeight.Bold,
                                color = c.textPrimary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (task.description.isNotBlank()) {
                                Spacer(Modifier.height(Space.xxs))
                                Text(
                                    task.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(Space.xs))
                        CheckRing(
                            completed = false,
                            accent = ink,
                            onToggle = onComplete,
                            size = 34.dp,
                            haptics = haptics,
                            contentDescription = "Mark ${task.title} done"
                        )
                    }
                    Spacer(Modifier.height(Space.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        urgencyLabel(task, urgency, now)?.let {
                            UrgencyChip(it, urgency, strong = true)
                            Spacer(Modifier.width(Space.xs))
                        }
                        PriorityBadge(task.priority)
                        Spacer(Modifier.weight(1f))
                        FocusPill(onClick = onStartFocus, haptics = haptics)
                    }
                }
            }
        }
    }
}

/** "Start 5 min" — the in-app twin of the pause screen's Tiny Win. */
@Composable
private fun FocusPill(onClick: () -> Unit, haptics: Haptics?) {
    val c = MaterialTheme.mindful
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, spring(0.45f, 600f), label = "focusPill")
    Row(
        Modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(Radius.chip)
            .background(Brush.horizontalGradient(listOf(c.accent, c.accentDeep)))
            .clickable(interactionSource = interaction, indication = null) {
                haptics?.click()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            "Start · 5 min",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/** Today's finished-vs-total as a ring that fills, with a gleam when it's full. */
@Composable
fun DayRing(done: Int, total: Int, size: Dp, showCount: Boolean = true) {
    val c = MaterialTheme.mindful
    val target = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, spring(dampingRatio = 0.75f, stiffness = 180f)) }

    val full = target >= 1f
    val shimmer = rememberInfiniteTransition(label = "ring")
    val gleam = shimmer.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "gleam"
    )
    val track = c.surfaceSunken
    val arc1 = c.accent
    val arc2 = c.accentDeep

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.13f
            val inset = stroke / 2f
            val rect = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), rect, style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(arc1, arc2, arc1), center = center),
                -90f, 360f * progress.value, false,
                Offset(inset, inset), rect, style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (full) {
                // A highlight travels round the finished ring
                val g = gleam.value
                drawArc(
                    Color.White.copy(alpha = 0.75f * (1f - g)),
                    -90f + g * 360f, 42f, false,
                    Offset(inset, inset), rect, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        if (showCount) {
            Text(
                if (full) "✓" else "$done",
                style = MaterialTheme.typography.labelMedium,
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Bold,
                color = if (full) c.accentDeep else c.textPrimary
            )
        }
    }
}

// ── List rows ──────────────────────────────────────────────────

/**
 * One task. Tapping the ring completes it in place — the row fades and
 * strikes through under your finger before the list re-sorts, so the
 * reward lands before the layout moves.
 */
@Composable
fun TaskListRow(
    task: Task,
    now: Long,
    haptics: Haptics?,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val c = MaterialTheme.mindful
    val urgency = remember(task, now) { urgencyOf(task, now) }
    val ink = urgencyInk(urgency)
    val hot = !task.isCompleted && (urgency == Urgency.Overdue || urgency == Urgency.Today)
    var justDone by remember(task.id) { mutableStateOf(false) }
    val fade by animateFloatAsState(if (justDone) 0.45f else 1f, tween(420), label = "rowFade")

    MindfulCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = fade },
        contentPadding = Space.xs,
        borderColor = if (hot) ink.copy(alpha = 0.35f) else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckRing(
                completed = task.isCompleted,
                accent = if (task.isCompleted) c.accent else ink,
                onToggle = {
                    if (!task.isCompleted) justDone = true
                    onToggle()
                },
                size = 26.dp,
                haptics = haptics,
                contentDescription = if (task.isCompleted) "Mark ${task.title} not done" else "Mark ${task.title} done"
            )

            Column(
                Modifier
                    .weight(1f)
                    .clip(Radius.field)
                    .clickable(onClick = onClick)
                    .padding(vertical = Space.xs, horizontal = Space.xxs)
            ) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (hot) FontWeight.Bold else FontWeight.Medium,
                    color = if (task.isCompleted || justDone) c.textSecondary else c.textPrimary,
                    textDecoration = if (task.isCompleted || justDone) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.description.isNotBlank()) {
                    Text(
                        task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!task.isCompleted) {
                urgencyLabel(task, urgency, now)?.let {
                    Spacer(Modifier.width(Space.xxs))
                    UrgencyChip(it, urgency)
                    Spacer(Modifier.width(Space.xxs))
                }
            }
            PriorityDot(task.priority)
            Spacer(Modifier.width(Space.xs))
        }
    }
}

/** Section heading with a count, used above each group of rows. */
@Composable
fun ListHeading(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    val c = MaterialTheme.mindful
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold,
            color = c.textSecondary
        )
        Spacer(Modifier.width(Space.xs))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(c.border)
        )
        if (trailing != null) {
            Spacer(Modifier.width(Space.xs))
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

/**
 * The day's shape at a glance: how much of the list is done, and what
 * FocusGuard has saved you from so far.
 */
@Composable
fun TodayCard(
    done: Int,
    total: Int,
    walkedAway: Int,
    focusMinutes: Int,
    instagramMinutes: Int,
    limitMinutes: Int,
    streak: Int,
    onOpenLimit: () -> Unit
) {
    val c = MaterialTheme.mindful
    MindfulCard(modifier = Modifier.fillMaxWidth(), shape = Radius.cardLarge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayRing(done = done, total = total.coerceAtLeast(1), size = 52.dp)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        total == 0 -> "Nothing on the list"
                        done >= total -> "Everything done today"
                        done == 0 -> "$total to go today"
                        else -> "$done of $total done"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary
                )
                Text(
                    buildString {
                        if (walkedAway > 0) append("$walkedAway walk-away${if (walkedAway == 1) "" else "s"}")
                        if (focusMinutes > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${focusMinutes}m focused")
                        }
                        if (isEmpty()) append("Small steps create big changes.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (streak > 0) {
                Spacer(Modifier.width(Space.xs))
                Row(
                    Modifier
                        .clip(Radius.chip)
                        .background(c.peach.copy(alpha = if (c.isDark) 0.22f else 0.7f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔥", fontSize = 12.sp)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "$streak",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.sm))
        InstagramBudgetBar(
            usedMinutes = instagramMinutes,
            limitMinutes = limitMinutes,
            onClick = onOpenLimit
        )
    }
}

/**
 * Today's Instagram minutes against the budget. With no budget set it is a
 * quiet invitation to set one; with a budget it fills and turns amber, then
 * red, as the minutes go.
 */
@Composable
fun InstagramBudgetBar(usedMinutes: Int, limitMinutes: Int, onClick: () -> Unit) {
    val c = MaterialTheme.mindful
    val hasLimit = limitMinutes > 0
    val fraction = if (hasLimit) (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f) else 0f
    val animated = remember { Animatable(0f) }
    LaunchedEffect(fraction) { animated.animateTo(fraction, tween(700, easing = FastOutSlowInEasing)) }

    val bar = when {
        !hasLimit -> c.accent
        fraction >= 1f -> c.danger
        fraction > 0.75f -> c.warning
        else -> c.accent
    }
    val track = c.surfaceSunken

    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.field)
            .clickable(onClick = onClick)
            .padding(vertical = Space.xxs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Instagram today",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (hasLimit) "$usedMinutes / $limitMinutes min" else "${usedMinutes}m · set a limit",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (hasLimit && fraction >= 1f) c.danger else c.textPrimary
            )
        }
        Spacer(Modifier.height(Space.xxs))
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(Radius.chip)
                .drawBehind {
                    drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
                    val w = size.width * animated.value
                    if (w > 0f) {
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(bar.copy(alpha = 0.75f), bar)),
                            size = Size(w.coerceAtLeast(size.height), size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                        )
                    }
                }
        )
    }
}
