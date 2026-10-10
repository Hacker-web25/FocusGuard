package com.dhruv.focusguard.guard.overlay

import android.os.SystemClock
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════
//  State + callbacks
// ═════════════════════════════════════════════════════════════════

enum class PauseMode { Pause, Breathe, Focus, Celebrate }

/** Everything the pause screen shows. Written by the service, read by Compose. */
@Stable
class PauseState {
    var userName by mutableStateOf("")
    var deadlineElapsed by mutableLongStateOf(0L)
    var totalMs by mutableLongStateOf(1L)
    var finished by mutableStateOf(false)
    var penaltySeconds by mutableIntStateOf(0)
    var strikes by mutableIntStateOf(0)
    var justPenalized by mutableStateOf(false)
    var tasks by mutableStateOf<List<Task>>(emptyList())
    var tasksLoaded by mutableStateOf(false)
    var goal by mutableStateOf<Goal?>(null)
    var opensToday by mutableIntStateOf(0)
    var walkAwaysToday by mutableIntStateOf(0)
    var mode by mutableStateOf(PauseMode.Pause)
    var focusTask by mutableStateOf<Task?>(null)
    var celebrateTitle by mutableStateOf("")
    var celebrateBody by mutableStateOf("")
}

/** What the pause screen asks the service to do. All calls are on the main thread. */
interface PauseActions {
    /** The countdown hit zero: Instagram may now be opened. */
    fun countdownFinished()
    /** User held "open Instagram" after the countdown. */
    fun continueToInstagram()
    /** User chose Breathe or Focus: leaving from here is not a skip attempt. */
    fun diverted()
    /** User chose not to open Instagram. Returns today's walk-away count including this one. */
    fun walkAway(): Int
    /** Celebration finished: remove the pause and go to the home screen. */
    fun celebrationDone()
    /** Mark a task complete from a focus sprint. */
    fun completeTask(task: Task)
    /** Record minutes spent in a focus sprint. */
    fun recordFocus(minutes: Int)
    /** Open FocusGuard to add a task. */
    fun addTask()
}

// ═════════════════════════════════════════════════════════════════
//  Palette
// ═════════════════════════════════════════════════════════════════

private val Night = Color(0xFF070B18)
private val Ink = Color(0xFFF7F5FF)
private val InkSoft = Color(0xD9F7F5FF)
private val InkMuted = Color(0x99F7F5FF)
private val InkFaint = Color(0x66F7F5FF)
private val Teal = Color(0xFF5FE0C0)
private val Violet = Color(0xFF6B4BFF)
private val Indigo = Color(0xFF2B2E83)

private val UrgOverdue = Color(0xFFFF3358)
private val UrgHours = Color(0xFFFF5245)
private val UrgSoon = Color(0xFFFF8A3D)
private val UrgWeek = Color(0xFFFFC24B)
private val UrgLater = Color(0xFF7FD8C0)
private val UrgNone = Color(0xFFB4BBD6)

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

// ═════════════════════════════════════════════════════════════════
//  Urgency
// ═════════════════════════════════════════════════════════════════

private enum class Urgency(val label: String, val color: Color) {
    Overdue("OVERDUE", UrgOverdue),
    Hours("DUE IN HOURS", UrgHours),
    Soon("DUE SOON", UrgSoon),
    Week("THIS WEEK", UrgWeek),
    Later("COMING UP", UrgLater),
    None("NO DEADLINE", UrgNone)
}

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

private fun urgencyOf(deadline: Long?, now: Long): Urgency {
    if (deadline == null) return Urgency.None
    val left = deadline - now
    return when {
        left < 0 -> Urgency.Overdue
        left < DAY -> Urgency.Hours
        left < 3 * DAY -> Urgency.Soon
        left < 7 * DAY -> Urgency.Week
        else -> Urgency.Later
    }
}

/** "10d 04h 12m 33s" / "04h 12m 33s" for the time between now and deadline. */
private fun longCountdown(deadline: Long, now: Long): String {
    var ms = kotlin.math.abs(deadline - now)
    val d = ms / DAY; ms %= DAY
    val h = ms / HOUR; ms %= HOUR
    val m = ms / 60_000; ms %= 60_000
    val s = ms / 1000
    return if (d > 0) "%dd %02dh %02dm %02ds".format(d, h, m, s)
    else "%02dh %02dm %02ds".format(h, m, s)
}

private fun shortLeft(deadline: Long?, now: Long): String {
    if (deadline == null) return "no date"
    val left = deadline - now
    return when {
        left < 0 -> "overdue"
        left < HOUR -> "${left / 60_000}m left"
        left < DAY -> "${left / HOUR}h left"
        else -> "${left / DAY}d left"
    }
}

private fun guiltLine(u: Urgency, deadline: Long?, now: Long): String = when (u) {
    Urgency.Overdue -> "This was due ${shortAgo(now - (deadline ?: now))} ago. Every reel pushes it further."
    Urgency.Hours -> "Hours left. Instagram will still be here tomorrow. This deadline won't."
    Urgency.Soon -> "${((deadline ?: now) - now) / DAY + 1} days. Have you actually started?"
    Urgency.Week -> "Less than a week. The reels can wait. This can't."
    Urgency.Later -> "Future you is counting on what you do in the next ten minutes."
    Urgency.None -> "No deadline is how things quietly never get done."
}

private fun shortAgo(ms: Long): String = when {
    ms < HOUR -> "${(ms / 60_000).coerceAtLeast(1)} min"
    ms < DAY -> "${ms / HOUR}h"
    else -> "${ms / DAY} day${if (ms / DAY == 1L) "" else "s"}"
}

private fun clock(ms: Long): String {
    val s = ((ms + 999) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Most urgent first: earliest deadline (overdue first), then priority. */
private fun byUrgency(tasks: List<Task>): List<Task> = tasks.sortedWith(
    compareBy<Task>({ it.deadline ?: Long.MAX_VALUE }, { -it.priority.weight })
)

// ═════════════════════════════════════════════════════════════════
//  Root
// ═════════════════════════════════════════════════════════════════

@Composable
fun PauseScreen(state: PauseState, actions: PauseActions, haptics: Haptics) {
    var remainingMs by remember { mutableLongStateOf(state.totalMs) }
    var nowWall by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // One clock drives everything: the pause countdown, live deadline timers,
    // last-five-seconds haptic ticks and the finish thud.
    LaunchedEffect(state.deadlineElapsed) {
        haptics.thud()
        var lastWhole = -1L
        while (true) {
            val rem = (state.deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            remainingMs = rem
            nowWall = System.currentTimeMillis()
            val whole = (rem + 999) / 1000
            if (whole != lastWhole) {
                if (lastWhole != -1L && whole in 1L..5L && state.mode == PauseMode.Pause) haptics.tick()
                lastWhole = whole
            }
            if (rem == 0L && !state.finished) {
                state.finished = true
                if (state.mode == PauseMode.Pause) haptics.thud()
                actions.countdownFinished()
            }
            delay(if (rem == 0L) 1000L else 100L)
        }
    }

    val sorted = remember(state.tasks) { byUrgency(state.tasks) }
    val hero = sorted.firstOrNull()
    val heroUrgency = urgencyOf(hero?.deadline, nowWall)
    val tint = if (hero == null) Violet else heroUrgency.color

    fun walkAway() {
        haptics.success()
        val n = actions.walkAway()
        state.celebrateTitle = "You walked away."
        state.celebrateBody = when (n) {
            1 -> "First time today. This is how it starts."
            else -> "That's $n times today. Future you says thanks."
        }
        state.mode = PauseMode.Celebrate
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
    ) {
        Aurora(tint = tint)

        Crossfade(
            targetState = state.mode,
            animationSpec = tween(450),
            label = "mode"
        ) { mode ->
            when (mode) {
                PauseMode.Pause -> PauseContent(
                    state = state,
                    sorted = sorted,
                    remainingMs = remainingMs,
                    nowWall = nowWall,
                    haptics = haptics,
                    onWalkAway = { walkAway() },
                    onBreathe = {
                        haptics.click()
                        actions.diverted()
                        state.mode = PauseMode.Breathe
                    },
                    onFocus = { task ->
                        haptics.click()
                        actions.diverted()
                        state.focusTask = task
                        state.mode = PauseMode.Focus
                    },
                    onAddTask = {
                        haptics.click()
                        actions.diverted()
                        actions.addTask()
                    },
                    onContinue = {
                        haptics.thud()
                        actions.continueToInstagram()
                    }
                )

                PauseMode.Breathe -> BreatheContent(
                    haptics = haptics,
                    onWalkAway = { walkAway() },
                    onBack = {
                        haptics.click()
                        state.mode = PauseMode.Pause
                    }
                )

                PauseMode.Focus -> FocusContent(
                    task = state.focusTask,
                    haptics = haptics,
                    onComplete = { task, minutes ->
                        haptics.success()
                        actions.recordFocus(minutes)
                        actions.completeTask(task)
                        actions.walkAway()
                        state.celebrateTitle = "Done."
                        state.celebrateBody = "“${task.title}” is off your list. That beat any reel."
                        state.mode = PauseMode.Celebrate
                    },
                    onSprintOver = { minutes ->
                        actions.recordFocus(minutes)
                        walkAway()
                    },
                    onBack = {
                        haptics.click()
                        state.mode = PauseMode.Pause
                    }
                )

                PauseMode.Celebrate -> CelebrateContent(
                    title = state.celebrateTitle,
                    body = state.celebrateBody,
                    onDone = { actions.celebrationDone() }
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Pause mode
// ═════════════════════════════════════════════════════════════════

@Composable
private fun PauseContent(
    state: PauseState,
    sorted: List<Task>,
    remainingMs: Long,
    nowWall: Long,
    haptics: Haptics,
    onWalkAway: () -> Unit,
    onBreathe: () -> Unit,
    onFocus: (Task) -> Unit,
    onAddTask: () -> Unit,
    onContinue: () -> Unit
) {
    val hero = sorted.firstOrNull()
    val others = sorted.drop(1).take(3)
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 16.dp)
        ) {
            Appear(0) {
                Header(
                    name = state.userName,
                    hour = hour,
                    opensToday = state.opensToday,
                    walkAways = state.walkAwaysToday,
                    remainingMs = remainingMs,
                    totalMs = state.totalMs,
                    finished = state.finished
                )
            }

            if (state.penaltySeconds > 0) {
                Spacer(Modifier.height(14.dp))
                Appear(1) {
                    PenaltyChip(state.penaltySeconds, state.strikes, state.justPenalized)
                }
            }

            Spacer(Modifier.height(18.dp))
            Appear(2) {
                when {
                    !state.tasksLoaded -> GlassCard(Modifier.height(220.dp)) { }
                    hero == null -> EmptyHero(onAddTask)
                    else -> HeroTask(hero, nowWall, onFocus = { onFocus(hero) })
                }
            }

            if (others.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Appear(3) { AlsoWaiting(others, nowWall, state.goal) }
            } else if (state.goal != null) {
                Spacer(Modifier.height(22.dp))
                Appear(3) { AlsoWaiting(emptyList(), nowWall, state.goal) }
            }

            Spacer(Modifier.height(22.dp))
            Appear(4) { RestCard(evening = hour >= 17 || hour < 4, onBreathe = onBreathe) }
        }

        // Pinned actions: walking away is always one tap; Instagram takes a hold.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Night.copy(alpha = 0.92f)))
                )
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 36.dp)
        ) {
            PressButton(
                text = "Close Instagram",
                onClick = onWalkAway,
                container = Ink,
                contentColor = Night,
                height = 60.dp
            )
            Spacer(Modifier.height(10.dp))
            HoldToOpen(
                enabled = state.finished,
                label = if (state.finished) "Hold to open Instagram anyway"
                else "Instagram unlocks in ${clock(remainingMs)}",
                haptics = haptics,
                onComplete = onContinue
            )
        }
    }
}

@Composable
private fun Header(
    name: String,
    hour: Int,
    opensToday: Int,
    walkAways: Int,
    remainingMs: Long,
    totalMs: Long,
    finished: Boolean
) {
    val greeting = when (hour) {
        in 4..11 -> "Morning"
        in 12..16 -> "Afternoon"
        else -> "Evening"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (name.isBlank()) "$greeting." else "$greeting, $name.",
                color = InkMuted,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append("Instagram open #").append(opensToday.coerceAtLeast(1)).append(" today")
                    if (walkAways > 0) append(" · walked away ").append(walkAways).append("×")
                },
                color = InkFaint,
                fontSize = 13.sp
            )
        }
        TimerRing(remainingMs = remainingMs, totalMs = totalMs, finished = finished, diameter = 64.dp)
    }
}

@Composable
private fun PenaltyChip(seconds: Int, strikes: Int, justPenalized: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(UrgOverdue.copy(alpha = 0.16f))
            .border(1.dp, UrgOverdue.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PulsingDot(UrgOverdue, 7.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (justPenalized) "+${seconds}s · you left the last pause early"
            else "+${seconds}s · $strikes skip attempt${if (strikes == 1) "" else "s"}",
            color = Color(0xFFFFB3C0),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HeroTask(task: Task, now: Long, onFocus: () -> Unit) {
    val u = urgencyOf(task.deadline, now)
    val color by animateColorAsState(u.color, tween(600), label = "heroColor")
    val ageDays = ((now - task.createdAt) / DAY).toInt()

    GlassCard(glow = color, strong = u == Urgency.Overdue || u == Urgency.Hours) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color, 9.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (u == Urgency.None) "YOUR TOP TASK" else u.label,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.weight(1f))
            if (task.priority == Priority.URGENT || task.priority == Priority.HIGH) {
                Text(
                    text = task.priority.label.uppercase(),
                    color = Night,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = task.title,
            color = Ink,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        val deadline = task.deadline
        if (deadline != null) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = longCountdown(deadline, now),
                    style = Tabular,
                    color = color,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (deadline < now) "overdue" else "left",
                    color = InkMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            val span = (deadline - task.createdAt).coerceAtLeast(1L)
            val fraction = ((deadline - now).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            DrainBar(fraction = fraction, color = color)
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = guiltLine(u, deadline, now),
            color = InkSoft,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
        if (ageDays >= 1) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "On your list for $ageDays day${if (ageDays == 1) "" else "s"}.",
                color = InkFaint,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(18.dp))
        PressButton(
            text = "Start it now · 5 min",
            onClick = onFocus,
            container = color,
            contentColor = Night,
            height = 52.dp
        )
    }
}

@Composable
private fun EmptyHero(onAddTask: () -> Unit) {
    GlassCard(glow = Violet) {
        Text(
            text = "YOUR LIST IS EMPTY",
            color = Violet.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "What have you been putting off?",
            color = Ink,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Instagram is filling the space your goals should. Write down one thing now.",
            color = InkSoft,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(18.dp))
        PressButton(
            text = "Add a task",
            onClick = onAddTask,
            container = Violet,
            contentColor = Ink,
            height = 52.dp
        )
    }
}

@Composable
private fun AlsoWaiting(tasks: List<Task>, now: Long, goal: Goal?) {
    if (tasks.isNotEmpty()) {
        SectionLabel("ALSO WAITING ON YOU")
        Spacer(Modifier.height(10.dp))
        GlassCard(padding = 6.dp) {
            tasks.forEachIndexed { i, t ->
                val u = urgencyOf(t.deadline, now)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(u.color)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = t.title,
                        color = Ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = shortLeft(t.deadline, now),
                        style = Tabular,
                        color = u.color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(u.color.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                if (i != tasks.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .height(1.dp)
                            .background(Ink.copy(alpha = 0.07f))
                    )
                }
            }
        }
    }
    if (goal != null) {
        Spacer(Modifier.height(16.dp))
        SectionLabel("THE BIGGER GOAL")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = goal.title,
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${goal.progress}%",
                    style = Tabular,
                    color = Teal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            DrainBar(fraction = goal.progress / 100f, color = Teal)
        }
    }
}

@Composable
private fun RestCard(evening: Boolean, onBreathe: () -> Unit) {
    GlassCard {
        Text(
            text = if (evening) "Long day?" else "Need a break?",
            color = Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Reels feel like rest but leave you more drained. One minute of slow breathing actually works.",
            color = InkMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(14.dp))
        PressButton(
            text = "Breathe for 1 minute",
            onClick = onBreathe,
            container = Teal.copy(alpha = 0.16f),
            contentColor = Teal,
            borderColor = Teal.copy(alpha = 0.5f),
            height = 50.dp
        )
    }
}

// ═════════════════════════════════════════════════════════════════
//  Breathe mode: box breathing, 4 cycles of 4-4-4-4 (≈64s)
// ═════════════════════════════════════════════════════════════════

@Composable
private fun BreatheContent(haptics: Haptics, onWalkAway: () -> Unit, onBack: () -> Unit) {
    val scale = remember { Animatable(0.55f) }
    var phase by remember { mutableStateOf("Get comfortable") }
    var cycle by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(800)
        repeat(4) { c ->
            cycle = c + 1
            phase = "Breathe in"
            haptics.breath()
            scale.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
            phase = "Hold"
            haptics.tick()
            delay(4000)
            phase = "Breathe out"
            haptics.breath()
            scale.animateTo(0.55f, tween(4000, easing = FastOutSlowInEasing))
            phase = "Hold"
            haptics.tick()
            delay(4000)
        }
        done = true
        phase = "Done"
        haptics.success()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (done) "ONE MINUTE, NO SCROLL" else if (cycle == 0) "BOX BREATHING" else "CYCLE $cycle OF 4",
            color = Teal,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .size(270.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
            ) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Teal.copy(alpha = 0.95f), Violet.copy(alpha = 0.55f), Color.Transparent)
                    ),
                    radius = this.size.minDimension / 2f
                )
                drawCircle(Ink.copy(alpha = 0.10f), radius = this.size.minDimension / 2f * 0.6f)
            }
            Text(
                text = phase,
                color = Ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (done) "That was better for you than a reel." else "Follow the circle. In through the nose.",
            color = InkSoft,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (done) {
            PressButton(text = "Close Instagram", onClick = onWalkAway, container = Ink, contentColor = Night, height = 60.dp)
            Spacer(Modifier.height(10.dp))
        }
        PressButton(
            text = if (done) "Back" else "Stop",
            onClick = onBack,
            container = Color.Transparent,
            contentColor = InkMuted,
            borderColor = Ink.copy(alpha = 0.18f),
            height = 50.dp
        )
    }
}

// ═════════════════════════════════════════════════════════════════
//  Focus mode: a 5-minute sprint on the most urgent task
// ═════════════════════════════════════════════════════════════════

private const val SPRINT_MS = 5 * 60_000L

@Composable
private fun FocusContent(
    task: Task?,
    haptics: Haptics,
    onComplete: (Task, Int) -> Unit,
    onSprintOver: (Int) -> Unit,
    onBack: () -> Unit
) {
    val start = remember { SystemClock.elapsedRealtime() }
    var left by remember { mutableLongStateOf(SPRINT_MS) }
    LaunchedEffect(start) {
        while (true) {
            left = (SPRINT_MS - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0L)
            if (left == 0L) {
                haptics.success()
                break
            }
            delay(250)
        }
    }
    val over = left == 0L
    val minutesSpent = (((SPRINT_MS - left) + 30_000) / 60_000).toInt().coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (over) "SPRINT COMPLETE" else "FOCUS SPRINT",
            color = UrgSoon,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = task?.title ?: "Your next step",
            color = Ink,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = 1f - left.toFloat() / SPRINT_MS.toFloat(),
                color = UrgSoon,
                diameter = 240.dp,
                stroke = 10.dp
            )
            Text(
                text = clock(left),
                style = Tabular,
                color = Ink,
                fontSize = 58.sp,
                fontWeight = FontWeight.Light
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (over) "Five minutes in. The hardest part is behind you."
            else "Phone down. Just start. Five minutes is enough to break the wall.",
            color = InkSoft,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (task != null) {
            PressButton(
                text = if (over) "Mark it done" else "It's done · mark complete",
                onClick = { onComplete(task, minutesSpent) },
                container = UrgSoon,
                contentColor = Night,
                height = 58.dp
            )
            Spacer(Modifier.height(10.dp))
        }
        if (over) {
            PressButton(
                text = "Close Instagram, keep going",
                onClick = { onSprintOver(minutesSpent) },
                container = Ink,
                contentColor = Night,
                height = 54.dp
            )
        } else {
            PressButton(
                text = "Back",
                onClick = onBack,
                container = Color.Transparent,
                contentColor = InkMuted,
                borderColor = Ink.copy(alpha = 0.18f),
                height = 50.dp
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Celebrate mode
// ═════════════════════════════════════════════════════════════════

@Composable
private fun CelebrateContent(title: String, body: String, onDone: () -> Unit) {
    val pop = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
        burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        delay(1100)
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val b = burst.value
                val rays = 16
                val base = this.size.minDimension * 0.26f
                for (i in 0 until rays) {
                    val angle = (i.toFloat() / rays) * 2f * PI.toFloat()
                    val r1 = base + b * this.size.minDimension * 0.18f
                    val r2 = r1 + (1f - b) * 22.dp.toPx() + 6.dp.toPx()
                    val c = if (i % 2 == 0) Teal else Violet
                    drawLine(
                        color = c.copy(alpha = (1f - b).coerceIn(0f, 1f)),
                        start = Offset(center.x + cos(angle) * r1, center.y + sin(angle) * r1),
                        end = Offset(center.x + cos(angle) * r2, center.y + sin(angle) * r2),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Teal, Color(0xFF3CC2A0)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Night, modifier = Modifier.size(58.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            color = Ink,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = body,
            color = InkSoft,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ═════════════════════════════════════════════════════════════════
//  Building blocks
// ═════════════════════════════════════════════════════════════════

/** Slow-drifting, blurred colour field. Tinted by the hero task's urgency. */
@Composable
private fun Aurora(tint: Color) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    val b by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "b"
    )
    val warm by animateColorAsState(tint, tween(900), label = "tint")

    Canvas(
        Modifier
            .fillMaxSize()
            .blur(70.dp)
    ) {
        val w = this.size.width
        val h = this.size.height
        fun blob(c: Color, x: Float, y: Float, r: Float) {
            drawCircle(
                brush = Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(x, y), radius = r),
                radius = r,
                center = Offset(x, y)
            )
        }
        blob(warm.copy(alpha = 0.50f), w * (0.10f + 0.55f * a), h * (0.14f + 0.10f * b), w * 0.80f)
        blob(Violet.copy(alpha = 0.40f), w * (0.95f - 0.45f * b), h * (0.50f + 0.15f * a), w * 0.85f)
        blob(Indigo.copy(alpha = 0.55f), w * (0.25f + 0.35f * b), h * (0.92f - 0.10f * a), w * 0.75f)
    }
    // Scrim keeps text legible over the brightest parts.
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Night.copy(alpha = 0.25f), Night.copy(alpha = 0.55f), Night.copy(alpha = 0.80f))
                )
            )
    )
}

/** Frosted-glass card: translucent fill, bright top edge, optional coloured halo. */
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    strong: Boolean = false,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val halo = if (glow != null) {
        val pulse = if (strong) {
            val t = rememberInfiniteTransition(label = "halo")
            val v by t.animateFloat(
                initialValue = 0.28f, targetValue = 0.55f,
                animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "haloA"
            )
            v
        } else 0.30f
        Modifier.drawBehind {
            val spread = 18.dp.toPx()
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(glow.copy(alpha = pulse), Color.Transparent),
                    center = Offset(this.size.width * 0.2f, 0f),
                    radius = this.size.width
                ),
                topLeft = Offset(-spread, -spread),
                size = Size(this.size.width + spread * 2, this.size.height + spread * 2),
                cornerRadius = CornerRadius(26.dp.toPx() + spread)
            )
        }
    } else Modifier

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(halo)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.11f), Ink.copy(alpha = 0.04f))))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        (glow ?: Ink).copy(alpha = if (glow != null) 0.55f else 0.26f),
                        Ink.copy(alpha = 0.05f)
                    )
                ),
                shape
            )
            .padding(padding),
        content = content
    )
}

/** Fades and lifts each section in, one after another. */
@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L * index)
        p.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = p.value
            translationY = (1f - p.value) * 36.dp.toPx()
        }
    ) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = InkFaint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
}

@Composable
private fun PulsingDot(color: Color, diameter: Dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val ring by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)),
        label = "ring"
    )
    Canvas(Modifier.size(diameter * 2)) {
        val r = diameter.toPx() / 2f
        drawCircle(color.copy(alpha = (1f - ring) * 0.5f), radius = r + ring * r)
        drawCircle(color, radius = r)
    }
}

@Composable
private fun DrainBar(fraction: Float, color: Color) {
    val f by animateFloatAsState(fraction, tween(700, easing = FastOutSlowInEasing), label = "drain")
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Ink.copy(alpha = 0.09f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(f.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)))
        )
    }
}

@Composable
private fun TimerRing(remainingMs: Long, totalMs: Long, finished: Boolean, diameter: Dp) {
    val target = if (totalMs <= 0L) 1f else 1f - (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        ProgressRing(progress = target, color = Teal, diameter = diameter, stroke = 5.dp)
        if (finished) {
            Icon(Icons.Rounded.Check, contentDescription = "Pause complete", tint = Teal, modifier = Modifier.size(26.dp))
        } else {
            Text(
                text = clock(remainingMs),
                style = Tabular,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ProgressRing(progress: Float, color: Color, diameter: Dp, stroke: Dp) {
    val sweep by animateFloatAsState(progress.coerceIn(0f, 1f), tween(250, easing = LinearEasing), label = "sweep")
    Canvas(Modifier.size(diameter)) {
        val s = stroke.toPx()
        val inset = s / 2f
        val arcSize = Size(this.size.width - s, this.size.height - s)
        drawArc(
            color = Ink.copy(alpha = 0.12f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(width = s)
        )
        drawArc(
            color = color,
            startAngle = -90f, sweepAngle = 360f * sweep, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(width = s, cap = StrokeCap.Round)
        )
    }
}

/** Button that squishes on press with a spring, like iOS. */
@Composable
private fun PressButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    height: Dp = 56.dp,
    fontSize: TextUnit = 17.sp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "press"
    )
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(container)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = contentColor, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Opening Instagram takes a deliberate 1.4s press-and-hold that fills up with
 * rising haptic ticks. Letting go early drains it back. Disabled until the
 * pause is complete.
 */
@Composable
private fun HoldToOpen(enabled: Boolean, label: String, haptics: Haptics, onComplete: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Ink.copy(alpha = if (enabled) 0.07f else 0.04f))
            .border(1.dp, Ink.copy(alpha = if (enabled) 0.16f else 0.08f), shape)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            val fill = scope.launch {
                                progress.animateTo(1f, tween(1400, easing = LinearEasing))
                                onComplete()
                            }
                            val ticks = scope.launch {
                                while (true) {
                                    haptics.tick()
                                    delay(140)
                                }
                            }
                            tryAwaitRelease()
                            ticks.cancel()
                            if (progress.value < 1f) {
                                fill.cancel()
                                scope.launch { progress.animateTo(0f, tween(250)) }
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(Ink.copy(alpha = 0.16f))
        )
        Text(
            text = label,
            style = Tabular,
            color = if (enabled) InkMuted else InkFaint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
