package com.dhruv.focusguard.guard.overlay

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import kotlinx.coroutines.delay
import java.util.Calendar

// ═════════════════════════════════════════════════════════════════
//  State + callbacks
// ═════════════════════════════════════════════════════════════════

enum class PauseMode { Pause, CalmOrb, Breathe, TinyWin, JustBe, Celebrate }

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
    var goals by mutableStateOf<List<Goal>>(emptyList())
    var tasksLoaded by mutableStateOf(false)
    var opensToday by mutableIntStateOf(0)
    var walkAwaysToday by mutableIntStateOf(0)
    var streak by mutableIntStateOf(0)
    var mode by mutableStateOf(PauseMode.Pause)
    var focusTask by mutableStateOf<Task?>(null)
    var celebrateTitle by mutableStateOf("")
    var celebrateBody by mutableStateOf("")
}

/** What the pause screen asks the service to do. All calls are on the main thread. */
interface PauseActions {
    /** The countdown hit zero: Instagram may now be opened. */
    fun countdownFinished()
    /** User held "Continue to Instagram" after the countdown. */
    fun continueToInstagram()
    /** User chose a reset (orb, breathe, tiny win, just be): leaving from here is not a skip. */
    fun diverted()
    /** User chose not to open Instagram. Returns today's walk-away count including this one. */
    fun walkAway(): Int
    /** Current walk-away streak in days. */
    fun streak(): Int
    /** Celebration finished: remove the pause and go to the home screen. */
    fun celebrationDone()
    /** Mark a task complete. */
    fun completeTask(task: Task)
    /** Record minutes spent in a focus sprint. */
    fun recordFocus(minutes: Int)
    /** Open FocusGuard straight into "add a task". */
    fun addTask()
    /** Open FocusGuard (settings = jump to Settings). */
    fun openApp(settings: Boolean)
}

const val TARGET_PACKAGE = "com.instagram.android"

// ═════════════════════════════════════════════════════════════════
//  Root
// ═════════════════════════════════════════════════════════════════

@Composable
fun PauseScreen(state: PauseState, actions: PauseActions, haptics: Haptics) {
    ScaledToWidth {
        val backdrop = rememberBackdrop()
        var screen by remember { mutableStateOf(Size(1080f, 2400f)) }
        var remainingMs by remember { mutableLongStateOf(state.totalMs) }
        val appIcon = rememberAppIcon(TARGET_PACKAGE)

        // One clock drives the countdown, its last-five-seconds haptic ticks
        // and the finish thud.
        LaunchedEffect(state.deadlineElapsed) {
            haptics.thud()
            var lastWhole = -1L
            while (true) {
                val rem = (state.deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                remainingMs = rem
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

        fun walkAway(title: String = "You walked away.") {
            haptics.success()
            val n = actions.walkAway()
            state.streak = actions.streak()
            state.celebrateTitle = title
            state.celebrateBody = when (n) {
                1 -> "First time today. This is how it starts."
                else -> "That’s $n times today. Future you says thanks."
            }
            state.mode = PauseMode.Celebrate
        }

        fun divert(mode: PauseMode) {
            actions.diverted()
            state.mode = mode
        }

        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalScreenSize provides screen,
            LocalTextStyle provides BaseText
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { screen = Size(it.width.toFloat(), it.height.toFloat()) }
            ) {
                BackdropLayer()
                FallingPetals()

                Crossfade(targetState = state.mode, animationSpec = tween(420), label = "mode") { mode ->
                    when (mode) {
                        PauseMode.Pause -> PauseHome(
                            state = state,
                            remainingMs = remainingMs,
                            appIcon = appIcon,
                            haptics = haptics,
                            onContinue = { actions.continueToInstagram() },
                            onWalkAway = { walkAway() },
                            onCalmOrb = { divert(PauseMode.CalmOrb) },
                            onBreathe = { divert(PauseMode.Breathe) },
                            onTinyWin = { task ->
                                state.focusTask = task
                                divert(PauseMode.TinyWin)
                            },
                            onJustBe = { divert(PauseMode.JustBe) },
                            onComplete = { task ->
                                actions.completeTask(task)
                                state.tasks = state.tasks.filterNot { it.id == task.id }
                            },
                            onAddTask = {
                                actions.diverted()
                                actions.addTask()
                            },
                            onOpenApp = { settings ->
                                actions.diverted()
                                actions.openApp(settings)
                            }
                        )

                        PauseMode.CalmOrb -> CalmOrbMode(
                            name = state.userName,
                            haptics = haptics,
                            onWalkAway = { walkAway("Calmer now.") },
                            onBack = { haptics.click(); state.mode = PauseMode.Pause }
                        )

                        PauseMode.Breathe -> BreatheMode(
                            haptics = haptics,
                            onWalkAway = { walkAway("Better than a reel.") },
                            onBack = { haptics.click(); state.mode = PauseMode.Pause }
                        )

                        PauseMode.TinyWin -> TinyWinMode(
                            task = state.focusTask,
                            haptics = haptics,
                            onComplete = { task, minutes ->
                                actions.recordFocus(minutes)
                                actions.completeTask(task)
                                state.tasks = state.tasks.filterNot { it.id == task.id }
                                walkAway("Done. ✨")
                                state.celebrateBody = "“${task.title}” is off your list. That beat any reel."
                            },
                            onSprintOver = { minutes ->
                                actions.recordFocus(minutes)
                                walkAway("Five minutes, won.")
                            },
                            onBack = { haptics.click(); state.mode = PauseMode.Pause }
                        )

                        PauseMode.JustBe -> JustBeMode(
                            haptics = haptics,
                            onWalkAway = { walkAway("That was yours.") },
                            onBack = { haptics.click(); state.mode = PauseMode.Pause }
                        )

                        PauseMode.Celebrate -> CelebrateMode(
                            title = state.celebrateTitle,
                            body = state.celebrateBody,
                            streak = state.streak,
                            haptics = haptics,
                            onDone = { actions.celebrationDone() }
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  The main pause screen (the mockup)
// ═════════════════════════════════════════════════════════════════

@Composable
private fun PauseHome(
    state: PauseState,
    remainingMs: Long,
    appIcon: ImageBitmap?,
    haptics: Haptics,
    onContinue: () -> Unit,
    onWalkAway: () -> Unit,
    onCalmOrb: () -> Unit,
    onBreathe: () -> Unit,
    onTinyWin: (Task?) -> Unit,
    onJustBe: () -> Unit,
    onComplete: (Task) -> Unit,
    onAddTask: () -> Unit,
    onOpenApp: (Boolean) -> Unit
) {
    val now = rememberNow()
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val sorted = remember(state.tasks) { byUrgency(state.tasks) }
    val goal = remember(state.goals) { state.goals.filter { it.progress < 100 }.minByOrNull { it.progress } }
    val nextStep = remember(goal, state.tasks) {
        goal?.let { g -> byUrgency(state.tasks.filter { it.goalId == g.id }).firstOrNull()?.title }
            ?: goal?.description?.takeIf { it.isNotBlank() }
    }
    val statusBar = rememberStatusBarHeight()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = statusBar, bottom = 8.dp)
        ) {
            Appear(0) { Header(state.userName, hour, state.streak) { haptics.click(); onOpenApp(true) } }
            Spacer(Modifier.height(10.dp))
            Appear(1) {
                TimerAndBubble(
                    name = state.userName,
                    appIcon = appIcon,
                    remainingMs = remainingMs,
                    totalMs = state.totalMs,
                    finished = state.finished,
                    haptics = haptics
                )
            }
            if (state.penaltySeconds > 0) {
                Spacer(Modifier.height(8.dp))
                PenaltyNote(state.penaltySeconds, state.strikes, state.justPenalized)
            }
            Spacer(Modifier.height(12.dp))
            Appear(2) {
                PriorityCard(
                    tasks = sorted,
                    goals = state.goals,
                    loaded = state.tasksLoaded,
                    now = now,
                    haptics = haptics,
                    onOpenTask = { onTinyWin(it) },
                    onComplete = onComplete,
                    onAddTask = { haptics.click(); onAddTask() }
                )
            }
            if (goal != null) {
                Spacer(Modifier.height(14.dp))
                Appear(3) { GoalCard(goal, nextStep, now, haptics) { onOpenApp(false) } }
            }
            Spacer(Modifier.height(14.dp))
            Appear(4) {
                ResetBar(
                    haptics = haptics,
                    onCalmOrb = onCalmOrb,
                    onBreathe = onBreathe,
                    onTinyWin = { onTinyWin(sorted.firstOrNull()) },
                    onJustBe = onJustBe
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // Pinned: always one hold away from Instagram, one tap from walking away.
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x33241F4A))))
                .padding(top = 12.dp, bottom = 26.dp)
        ) {
            BottomActions(
                unlocked = state.finished,
                remainingLabel = clockLabel(remainingMs),
                appIcon = appIcon,
                haptics = haptics,
                onContinue = onContinue,
                onWalkAway = onWalkAway
            )
        }
    }
}

@Composable
private fun PenaltyNote(seconds: Int, strikes: Int, justPenalized: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        androidx.compose.material3.Text(
            text = if (justPenalized) "+${seconds}s added · you left the last pause early"
            else "+${seconds}s penalty · $strikes skip attempt${if (strikes == 1) "" else "s"} · sit through one to clear it",
            style = OnSky,
            fontSize = 12.sp,
            color = Color(0xFFFFE0E8),
            modifier = Modifier
                .background(Color(0x55E2304F), androidx.compose.foundation.shape.RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

fun clockLabel(ms: Long): String {
    val s = ((ms + 999) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Fades and lifts each section in, one after another. */
@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        p.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = p.value
            translationY = (1f - p.value) * 28.dp.toPx()
        }
    ) { content() }
}

@Composable
fun rememberAppIcon(pkg: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(pkg) {
        try {
            context.packageManager.getApplicationIcon(pkg).toBitmap(192, 192).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

/** Real status-bar height; the overlay window draws underneath it. */
@Composable
fun rememberStatusBarHeight(): androidx.compose.ui.unit.Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(density) {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val px = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        with(density) { (px.coerceAtLeast(1)).toDp() } + 6.dp
    }
}
