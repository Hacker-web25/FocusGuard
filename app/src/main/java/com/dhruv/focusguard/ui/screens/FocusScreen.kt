package com.dhruv.focusguard.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.R
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.guard.overlay.Glass
import com.dhruv.focusguard.guard.overlay.gradientTint
import com.dhruv.focusguard.ui.focus.FocusClock
import com.dhruv.focusguard.ui.focus.FocusMode
import com.dhruv.focusguard.ui.focus.FocusPhase
import com.dhruv.focusguard.ui.focus.FocusSession
import com.dhruv.focusguard.ui.focus.clockText
import com.dhruv.focusguard.ui.sky.AudienceStage
import com.dhruv.focusguard.ui.sky.GSans
import com.dhruv.focusguard.ui.sky.STAGE_TOP
import com.dhruv.focusguard.ui.sky.Sky
import com.dhruv.focusguard.ui.sky.SkyFit
import com.dhruv.focusguard.ui.sky.SkyScaffold
import com.dhruv.focusguard.ui.sky.StagePhase
import com.dhruv.focusguard.ui.sky.TaskKind
import com.dhruv.focusguard.ui.sky.at
import com.dhruv.focusguard.ui.sky.baselineAt
import com.dhruv.focusguard.ui.sky.calloutFor
import com.dhruv.focusguard.ui.sky.centerAt
import com.dhruv.focusguard.ui.sky.clockPoint
import com.dhruv.focusguard.ui.sky.gs
import com.dhruv.focusguard.ui.sky.isHighPriority
import com.dhruv.focusguard.ui.sky.kindOf
import com.dhruv.focusguard.ui.sky.longDate
import com.dhruv.focusguard.ui.sky.mp
import com.dhruv.focusguard.ui.sky.mpx
import com.dhruv.focusguard.ui.sky.sparkle
import com.dhruv.focusguard.ui.sky.subtitleOf
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════
//  Focus: one screen for the timer and the stopwatch.
// ═════════════════════════════════════════════════════════════════

@Composable
fun FocusRoute(
    viewModel: TaskViewModel,
    mode: FocusMode,
    taskId: Long?,
    onBack: () -> Unit,
    onEditTask: (Long) -> Unit
) {
    val context = LocalContext.current
    val active by viewModel.activeTasks.collectAsState()
    val completed by viewModel.completedTasks.collectAsState()
    LaunchedEffect(mode, taskId) {
        val t = taskId?.let { viewModel.getTaskById(it) }
        FocusClock.prepare(context, mode, taskId, t?.durationMinutes)
    }
    val session by FocusClock.session.collectAsState()
    val s = session ?: return
    val task = (active + completed).firstOrNull { it.id == s.taskId }

    val today = remember { System.currentTimeMillis() }
    val todays = remember(active, completed) {
        com.dhruv.focusguard.ui.sky.tasksForDay(active, completed, today, today)
    }
    val dayProgress = if (todays.isEmpty()) 0f else todays.count { it.isCompleted } / todays.size.toFloat()

    FocusContent(
        session = s,
        task = task,
        dayProgress = dayProgress,
        dateMillis = today,
        pickable = active,
        onBack = onBack,
        onChange = { change -> FocusClock.update(context, change) },
        onOpenTask = { t -> onEditTask(t.id) },
        onPickTask = { t ->
            FocusClock.update(context) { it.copy(taskId = t.id).let { n -> if (t.durationMinutes > 0 && n.mode == FocusMode.Timer) n.withTarget(t.durationMinutes * 60_000L) else n } }
        },
        onMarkDone = { t -> if (!t.isCompleted) viewModel.toggleComplete(t) }
    )
}

/**
 * The focus screen, laid out from the mockup. Every group keeps its mockup
 * size; on a phone taller than the mockup the gaps between groups grow in
 * proportion, so the composition stays the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusContent(
    session: FocusSession,
    task: Task?,
    dayProgress: Float,
    dateMillis: Long,
    pickable: List<Task>,
    onBack: () -> Unit,
    onChange: ((FocusSession) -> FocusSession) -> Unit,
    onOpenTask: (Task) -> Unit,
    onPickTask: (Task) -> Unit,
    onMarkDone: (Task) -> Unit,
    reference: Boolean = false,
    clock: () -> Long = { FocusClock.now() }
) {
    val context = LocalContext.current
    val haptics = remember { runCatching { Haptics(context) }.getOrNull() }
    val scope = rememberCoroutineScope()

    // Frame clock while counting; read through lambdas so only what draws it updates.
    val now = remember { mutableLongStateOf(clock()) }
    LaunchedEffect(session.running) {
        now.longValue = clock()
        while (session.running) {
            withInfiniteAnimationFrameNanos { now.longValue = clock() }
        }
    }
    val phase by remember(session) { derivedStateOf { session.phase(now.longValue) } }
    val progress: () -> Float = { session.progress(now.longValue) }
    val wholeSeconds by remember(session) {
        derivedStateOf {
            if (session.mode == FocusMode.Timer) ceil(session.remaining(now.longValue) / 1000.0).toLong()
            else floor(session.elapsed(now.longValue) / 1000.0).toLong()
        }
    }

    // The timer reaching zero
    LaunchedEffect(session) {
        if (session.mode == FocusMode.Timer && session.running) {
            val left = session.remaining(clock())
            delay(left)
            onChange { it.finish(clock()) }
            haptics?.success()
        }
    }

    // Keep the screen on while counting
    val view = LocalView.current
    DisposableEffect(session.running) {
        view.keepScreenOn = session.running
        onDispose { view.keepScreenOn = false }
    }

    // Audience: how many are cheering (0..5) and a wave at each quarter.
    // Progress is read in 5% steps so this recomposes rarely, not every frame.
    val p by remember(session) { derivedStateOf { floor(session.progress(now.longValue) * 20f) / 20f } }
    val cheer = when (phase) {
        FocusPhase.Finished -> 5
        FocusPhase.Idle -> (dayProgress * 5).roundToInt()
        else -> ceil(p * 5f).toInt().coerceIn(if (phase == FocusPhase.Running) 1 else 0, 5)
    }
    val wave by remember(session) { derivedStateOf { floor(session.progress(now.longValue) * 4f).toInt() } }
    val stagePhase = when (phase) {
        FocusPhase.Idle -> StagePhase.Idle
        FocusPhase.Running -> StagePhase.Running
        FocusPhase.Paused -> StagePhase.Paused
        FocusPhase.Finished -> StagePhase.Finished
    }
    val callout = if (reference) "Almost there!" else if (session.mode == FocusMode.Stopwatch && phase == FocusPhase.Running && p >= 1f) "Goal reached!" else calloutFor(stagePhase, p)

    var showDurations by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    SkyScaffold(res = R.drawable.sky_focus, fit = SkyFit.Cover) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ky = (maxHeight.value / (1536f * com.dhruv.focusguard.ui.sky.MP)).coerceAtLeast(1f)
            fun y(v: Number) = v.toFloat() * ky

            // ── Top bar ────────────────────────────────────────────
            BackButton(onBack, Modifier.centerAt(88, y(124)))
            DateChip(dateMillis, Modifier.centerAt(857, y(125.5f)))

            // ── The task ───────────────────────────────────────────
            TaskBanner(
                task = task,
                mode = session.mode,
                onClick = {
                    if (task != null) onOpenTask(task)
                    else if (phase == FocusPhase.Idle && pickable.isNotEmpty()) showPicker = true
                },
                modifier = Modifier.centerAt(512.5f, y(250))
            )

            // ── The ring ───────────────────────────────────────────
            TimerRing(
                session = session,
                phase = phase,
                progress = progress,
                seconds = wholeSeconds,
                reference = reference,
                haptics = haptics,
                onSetTarget = { ms -> onChange { it.withTarget(ms) } },
                onTapTime = { if (session.mode == FocusMode.Timer && phase == FocusPhase.Idle) showDurations = true },
                modifier = Modifier.centerAt(512, y(622))
            )

            // ── Controls ───────────────────────────────────────────
            PrimaryPill(
                phase = phase,
                hasTask = task != null && !task.isCompleted,
                onClick = {
                    haptics?.click()
                    val t = clock()
                    when (phase) {
                        FocusPhase.Idle, FocusPhase.Paused -> onChange { it.start(t) }
                        FocusPhase.Running -> onChange { it.pause(t) }
                        FocusPhase.Finished -> if (task != null && !task.isCompleted) {
                            onMarkDone(task); haptics?.success()
                        } else onChange { it.reset().start(t) }
                    }
                },
                modifier = Modifier.centerAt(511, y(955))
            )
            ResetPill(
                enabled = phase != FocusPhase.Idle,
                onClick = { haptics?.tick(); onChange { it.reset() } },
                modifier = Modifier.centerAt(512, y(1044))
            )

            // ── The audience, standing on their card ───────────────
            val cardTop = y(1347)
            AudienceStage(
                phase = stagePhase,
                cheer = cheer,
                wave = if (phase == FocusPhase.Running || phase == FocusPhase.Paused) wave else 0,
                callout = callout,
                haptics = haptics,
                referenceFaces = reference,
                modifier = Modifier.at(0, cardTop - (1347 - STAGE_TOP))
            )
            AudienceCard(
                phase = stagePhase,
                cheer = if (reference) 3 else cheer,
                modifier = Modifier.at(60, cardTop)
            )
        }
    }

    if (showDurations) {
        DurationSheet(
            current = session.targetMs,
            onPick = { ms -> onChange { it.withTarget(ms) }; showDurations = false },
            onDismiss = { showDurations = false }
        )
    }
    if (showPicker) {
        TaskPickerSheet(
            tasks = pickable,
            onPick = { t -> onPickTask(t); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

// ── Pieces ─────────────────────────────────────────────────────────

@Composable
private fun BackButton(onClick: () -> Unit, modifier: Modifier) {
    Glass(
        modifier = modifier.size(72.mp).clickable(role = Role.Button, onClickLabel = "Back", onClick = onClick),
        shape = CircleShape,
        tint = Color.White.copy(alpha = 0.32f),
        rim = Color.White.copy(alpha = 0.75f),
        elevation = 2.dp
    ) {
        Icon(
            Icons.Rounded.ArrowBackIosNew, contentDescription = "Back", tint = Color.White,
            modifier = Modifier.align(Alignment.Center).padding(end = 2.mp).size(26.mp)
        )
    }
}

@Composable
private fun DateChip(dateMillis: Long, modifier: Modifier) {
    Glass(
        modifier = modifier.size(230.mp, 55.mp),
        shape = RoundedCornerShape(50),
        tint = Color.White.copy(alpha = 0.55f),
        rim = Color.White.copy(alpha = 0.8f),
        elevation = 2.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            Icon(Icons.Outlined.CalendarToday, null, tint = Sky.GlassChipInk, modifier = Modifier.at(23, 13).size(28.mp))
            Text(longDate(dateMillis), style = gs(20.7f, color = Sky.GlassChipInk), modifier = Modifier.baselineAt(63, 33.5f))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color(0xFF8A90C4), modifier = Modifier.at(196, 14).size(27.mp))
        }
    }
}

@Composable
private fun TaskBanner(task: Task?, mode: FocusMode, onClick: () -> Unit, modifier: Modifier) {
    val kind = task?.let { kindOf(it) } ?: TaskKind.General
    Glass(
        modifier = modifier
            .size(921.mp, 136.mp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(48.mp),
        tint = Color.White.copy(alpha = 0.58f),
        rim = Color.White.copy(alpha = 0.85f),
        elevation = 3.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            // origin = mockup (52, 182)
            Box(
                Modifier.centerAt(131 - 52, 246 - 182).size(82.mp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFE6E1FE), Color(0xFFD7CFFB))))
            ) {
                Icon(
                    if (task == null) Icons.Outlined.Timer else kind.icon,
                    null, tint = Color(0xFF5D50C5), modifier = Modifier.align(Alignment.Center).size(40.mp)
                )
            }
            val title = task?.title ?: if (mode == FocusMode.Timer) "Free focus" else "Stopwatch"
            val sub = task?.let { subtitleOf(it) } ?: "Tap to pick a task"
            Text(
                title, style = gs(26.1f, FontWeight.Medium, Color(0xFF071859)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width((if (task?.isHighPriority == true) 500 else 650).mp).baselineAt(204 - 52, 240.3f - 182)
            )
            Text(
                sub, style = gs(21.6f, color = Color(0xFF6B70A8)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(650.mp).baselineAt(204 - 52, 276.3f - 182)
            )
            if (task?.isHighPriority == true) {
                PriorityChip(Modifier.at(715 - 52, 215 - 182))
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF8286B5), modifier = Modifier.centerAt(909 - 52, 249 - 182).size(38.mp))
        }
    }
}

@Composable
fun PriorityChip(modifier: Modifier = Modifier, height: Number = 40, textSize: Float = 18.4f) {
    Row(
        modifier
            .height(height.mp)
            .clip(RoundedCornerShape(50))
            .background(Sky.RedChip)
            .padding(start = 14.mp, end = 18.mp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.LocalFireDepartment, null,
            modifier = Modifier.size(24.mp).gradientTint(Brush.verticalGradient(listOf(Color(0xFFFFB347), Color(0xFFF25A4E), Color(0xFFE8403F)))),
            tint = Color.White
        )
        Spacer(Modifier.width(10.mp))
        Text("High priority", style = gs(textSize, FontWeight.Medium, Sky.Red))
    }
}

/**
 * The glass ring: track, progress arc with its white knob, and the time.
 * While the timer is not started, drag round the ring to set its length
 * (one lap = an hour), or tap the time for presets.
 */
@Composable
private fun TimerRing(
    session: FocusSession,
    phase: FocusPhase,
    progress: () -> Float,
    seconds: Long,
    reference: Boolean,
    haptics: Haptics?,
    onSetTarget: (Long) -> Unit,
    onTapTime: () -> Unit,
    modifier: Modifier
) {
    // Group box: mockup (172, 282) .. (852, 962), ring centre at (340, 340)
    val canSet = session.mode == FocusMode.Timer && phase == FocusPhase.Idle
    var dragMinutes by remember { mutableStateOf<Int?>(null) }
    val dragArc by animateFloatAsState(
        targetValue = dragMinutes?.let { ((it % 60).takeIf { m -> m != 0 } ?: 60) / 60f } ?: 0f,
        animationSpec = if (dragMinutes != null) spring(stiffness = 900f) else tween(450, easing = FastOutSlowInEasing),
        label = "dragArc"
    )
    val pulse = rememberInfiniteTransition(label = "ring")
    val glowT = pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "glow")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(phase) {
        if (phase == FocusPhase.Running || phase == FocusPhase.Finished) {
            pop.animateTo(1.035f, tween(140)); pop.animateTo(1f, spring(0.35f, 300f))
        }
    }

    Box(modifier.size(680.mp).scale(pop.value)) {
        Glass(
            modifier = Modifier.centerAt(340, 340).size(532.mp),
            shape = CircleShape,
            tint = Color.White.copy(alpha = 0.42f),
            rim = Color.White.copy(alpha = 0.95f),
            elevation = 0.dp
        ) {}
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(canSet) {
                    if (!canSet) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = (down.position - c).getDistance() / (com.dhruv.focusguard.ui.sky.MP * density)
                        if (r < 190f || r > 300f) return@awaitEachGesture // only the ring itself
                        fun angle(pos: Offset): Float {
                            val d = pos - c
                            var a = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).toFloat() + 90f
                            if (a < 0) a += 360f
                            return a
                        }
                        var lastA = angle(down.position)
                        val startMin = (session.targetMs / 60_000L).toInt()
                        var total = startMin.toFloat()
                        dragMinutes = startMin
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val a = angle(ch.position)
                            var d = a - lastA
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            lastA = a
                            total = (total + d / 6f).coerceIn(1f, 240f)  // 6° per minute
                            val m = total.roundToInt()
                            if (m != dragMinutes) {
                                if (m % 5 == 0) haptics?.click() else haptics?.tick()
                                dragMinutes = m
                                onSetTarget(m * 60_000L)
                            }
                            ch.consume()
                        }
                        dragMinutes = null
                    }
                }
        ) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val u = mpx(1)
            // Light around the ring, breathing while it runs
            val breathe = if (phase == FocusPhase.Running) 0.08f * sin(glowT.value * 6.283f) else 0f
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Transparent, 0.70f to Color.Transparent,
                    0.78f to Color.White.copy(alpha = 0.55f + breathe), 1f to Color.Transparent,
                    center = c, radius = 340 * u
                ),
                radius = 340 * u, center = c
            )
            // Inner disk, whiter than the outer glass
            drawCircle(Sky.Disk.copy(alpha = 0.78f), radius = 229 * u, center = c)
            drawCircle(Color.White.copy(alpha = 0.7f), radius = 229 * u, center = c, style = Stroke(2.2f * u))

            val band = 25 * u
            val rr = 242.5f * u
            val arcTopLeft = Offset(c.x - rr, c.y - rr)
            val arcSize = Size(rr * 2, rr * 2)
            // Track: lavender on the left, pink on the right
            rotate(-90f, c) {
                drawArc(
                    Brush.sweepGradient(
                        0f to Color(0xFFE7D4FB), 0.25f to Color(0xFFF6CEF3), 0.5f to Color(0xFFF3DDF8),
                        0.75f to Color(0xFFDBD2F9), 1f to Color(0xFFE7D4FB), center = c
                    ),
                    0f, 360f, false, topLeft = arcTopLeft, size = arcSize, style = Stroke(band)
                )
            }
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 259 * u, center = c, style = Stroke(5 * u))

            // Progress (or the length being dialled in)
            val shown = if (reference) 0.111f else if (dragMinutes != null || dragArc > 0.001f) dragArc else progress()
            val sweep = 360f * shown.coerceIn(0f, 1f)
            if (sweep > 0.5f) {
                rotate(-90f, c) {
                    drawArc(
                        Brush.sweepGradient(0f to Sky.ArcFrom, (shown.coerceAtLeast(0.05f)) to Sky.ArcTo, 1f to Sky.ArcTo, center = c),
                        0f, sweep, false, topLeft = arcTopLeft, size = arcSize, style = Stroke(band, cap = StrokeCap.Round)
                    )
                }
            }
            val head = clockPoint(c, rr, sweep)
            // Knob: glow, shadow, body
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color(0xFFF2C6F7).copy(alpha = 0.5f), Color.Transparent), center = head, radius = 34 * u),
                radius = 34 * u, center = head
            )
            drawCircle(Color(0xFFC79BEF).copy(alpha = 0.35f), radius = 17.5f * u, center = head + Offset(0f, 2 * u))
            drawCircle(Color.White, radius = 16.5f * u, center = head)
            if (phase == FocusPhase.Running) {
                // A sparkle riding just behind the knob
                val tw = 0.5f + 0.5f * sin(glowT.value * 12.566f)
                sparkle(clockPoint(c, rr, (sweep - 9f).coerceAtLeast(0f)), 7 * u * (0.6f + tw * 0.6f), Color.White.copy(alpha = 0.9f))
            }
        }

        // Contents (relative to the group origin 172, 282)
        val inkStopwatch = Color(0xFF414C88)
        Icon(Icons.Outlined.Timer, null, tint = inkStopwatch, modifier = Modifier.centerAt(512 - 172, 477 - 282).size(38.mp))
        val label = when {
            session.mode == FocusMode.Stopwatch -> if (phase == FocusPhase.Idle) "Stopwatch" else "Time focused"
            phase == FocusPhase.Finished -> "Time's up"
            phase == FocusPhase.Paused -> "Paused"
            else -> "Focus time"
        }
        Text(label, style = gs(25.7f, color = Sky.FocusLabel), modifier = Modifier.baselineAt(512 - 172, 535.5f - 282, centered = true))

        val text = if (reference) "1:30:00" else clockText(seconds * 1000L, forceHours = session.targetMs >= 3_600_000L && session.mode == FocusMode.Timer)
        RollingClock(
            text = text,
            modifier = Modifier
                .baselineAt(512 - 172, 643.3f - 282, centered = true)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    enabled = canSet, onClick = onTapTime
                )
        )

        val (line1, line2) = when (phase) {
            FocusPhase.Idle -> if (canSet) "Drag the ring to set" to "or tap the time" else "Stay focused" to "You got this!"
            FocusPhase.Paused -> "Take a breath" to "Resume when ready"
            FocusPhase.Finished -> "Well done" to "You showed up!"
            FocusPhase.Running -> "Stay focused" to "You got this!"
        }
        Row(Modifier.baselineAt(512 - 172, 715.3f - 282, centered = true), verticalAlignment = Alignment.CenterVertically) {
            TargetIcon(Modifier.size(28.mp))
            Spacer(Modifier.width(15.mp))
            Text(line1, style = gs(21.6f, color = Sky.StayInk))
        }
        Text(line2, style = gs(23.6f, color = Color(0xFF6D74A5)), modifier = Modifier.baselineAt(512 - 172, 758.6f - 282, centered = true))
    }
}

/** The time, each digit rolling up as it changes. */
@Composable
private fun RollingClock(text: String, modifier: Modifier = Modifier) {
    val style = gs(86.7f, FontWeight.SemiBold, Sky.TimerInk).copy(fontFeatureSettings = "tnum")
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            if (ch.isDigit()) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(160)))
                    },
                    label = "digit$i"
                ) { d -> Text(d.toString(), style = style) }
            } else {
                Text(ch.toString(), style = style)
            }
        }
    }
}

/** A dart in a bullseye, like the mockup's "Stay focused" mark. */
@Composable
private fun TargetIcon(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * 0.46f, size.height * 0.54f)
        val r = size.minDimension * 0.42f
        val ink = Sky.Target
        drawCircle(ink, radius = r, center = c, style = Stroke(size.minDimension * 0.10f))
        drawCircle(ink, radius = r * 0.55f, center = c, style = Stroke(size.minDimension * 0.10f))
        drawCircle(ink, radius = r * 0.18f, center = c)
        val tip = c
        val tail = Offset(size.width * 0.95f, size.height * 0.08f)
        drawLine(Color.White, tip, tail, strokeWidth = size.minDimension * 0.22f, cap = StrokeCap.Round)
        drawLine(ink, tip, tail, strokeWidth = size.minDimension * 0.10f, cap = StrokeCap.Round)
        drawLine(ink, tail, Offset(tail.x - size.width * 0.20f, tail.y), strokeWidth = size.minDimension * 0.09f, cap = StrokeCap.Round)
        drawLine(ink, tail, Offset(tail.x, tail.y + size.height * 0.20f), strokeWidth = size.minDimension * 0.09f, cap = StrokeCap.Round)
    }
}

@Composable
private fun PrimaryPill(phase: FocusPhase, hasTask: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.5f, 700f), label = "press")
    val (icon, label) = when (phase) {
        FocusPhase.Idle -> Icons.Rounded.PlayArrow to "Start"
        FocusPhase.Running -> Icons.Rounded.Pause to "Pause"
        FocusPhase.Paused -> Icons.Rounded.PlayArrow to "Resume"
        FocusPhase.Finished -> if (hasTask) Icons.Rounded.Check to "Mark done" else Icons.Rounded.Replay to "Again"
    }
    Box(
        modifier
            .size(298.mp, 76.mp)
            .scale(s)
            .drawBehind {
                // purple glow under the button
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF9B7CF6).copy(alpha = 0.30f)), startY = size.height * 0.4f, endY = size.height * 1.25f),
                    topLeft = Offset(size.width * 0.06f, size.height * 0.25f), size = Size(size.width * 0.88f, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )
            }
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(*Sky.PauseStops))
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), endY = size.height * 0.55f))
                drawRoundRect(
                    Color.White.copy(alpha = 0.55f), style = Stroke(2.mp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(targetState = icon to label, label = "primary") { (ic, lb) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, null, tint = Color.White, modifier = Modifier.size(38.mp))
                Spacer(Modifier.width(9.mp))
                Text(lb, style = gs(25.3f, FontWeight.Medium, Color.White))
            }
        }
    }
}

@Composable
private fun ResetPill(enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Glass(
        modifier = modifier
            .size(170.mp, 56.mp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.75f }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(50),
        tint = Color.White.copy(alpha = 0.40f),
        rim = Color.White.copy(alpha = 0.7f),
        elevation = 0.dp
    ) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.RestartAlt, null, tint = Color(0xFF777CB1), modifier = Modifier.size(28.mp))
            Spacer(Modifier.width(12.mp))
            Text("Reset", style = gs(21.1f, FontWeight.Medium, Sky.ResetInk))
        }
    }
}

@Composable
private fun AudienceCard(phase: StagePhase, cheer: Int, modifier: Modifier) {
    val fill by animateFloatAsState(cheer / 5f, tween(700, easing = FastOutSlowInEasing), label = "aud")
    val sub = when (phase) {
        StagePhase.Idle -> "They're waiting for you"
        StagePhase.Running -> "They’re cheering for you!"
        StagePhase.Paused -> "They're holding their breath"
        StagePhase.Finished -> "They're so proud of you!"
    }
    Glass(
        modifier = modifier.size(903.mp, 108.mp),
        shape = RoundedCornerShape(46.mp),
        tint = Color.White.copy(alpha = 0.50f),
        rim = Color.White.copy(alpha = 0.85f),
        elevation = 3.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            // origin = mockup (60, 1347)
            Box(Modifier.centerAt(133 - 60, 1402 - 1347).size(66.mp).clip(CircleShape).background(Sky.AudTile)) {
                Icon(Icons.Outlined.Groups, null, tint = Sky.AudIcon, modifier = Modifier.align(Alignment.Center).size(38.mp))
            }
            Text("Audience", style = gs(18.9f, FontWeight.Medium, Color(0xFF172268)), modifier = Modifier.baselineAt(189 - 60, 1395.6f - 1347))
            Text(sub, style = gs(16.4f, color = Color(0xFF7472AA)), modifier = Modifier.baselineAt(189 - 60, 1421.2f - 1347))
            Canvas(Modifier.at(616 - 60, 1395 - 1347).size(246.mp, 13.mp)) {
                val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                drawRoundRect(Sky.BarTrack, cornerRadius = r)
                if (fill > 0f) {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Color(0xFF8C8BFD), Color(0xFF9A93FD))),
                        size = Size(size.width * fill.coerceIn(0.05f, 1f), size.height), cornerRadius = r
                    )
                }
            }
            Text("$cheer/5", style = gs(18.3f, color = Color(0xFF7F7AB5)), modifier = Modifier.baselineAt(883 - 60, 1406.6f - 1347))
        }
    }
}

// ── Sheets ─────────────────────────────────────────────────────────

private val PRESETS = listOf(5, 10, 15, 20, 25, 30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationSheet(current: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf((current / 60_000L).toInt()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFFF7F3FC)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Focus for", style = gs(40, FontWeight.Medium, Sky.Navy))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                SheetRound("–") { minutes = (minutes - 1).coerceAtLeast(1) }
                Text(
                    com.dhruv.focusguard.ui.sky.durationText(minutes).ifEmpty { "0m" },
                    style = gs(70, FontWeight.SemiBold, Sky.TimerInk),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                SheetRound("+") { minutes = (minutes + 1).coerceAtMost(240) }
            }
            Spacer(Modifier.height(16.dp))
            PRESETS.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        val sel = m == minutes
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(50))
                                .background(if (sel) Color(0xFF9A90FB) else Color(0xFFE9E3FA))
                                .clickable { minutes = m },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(com.dhruv.focusguard.ui.sky.durationText(m), style = gs(34, FontWeight.Medium, if (sel) Color.White else Color(0xFF5149A9)))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(*Sky.PauseStops))
                    .clickable { onPick(minutes * 60_000L) },
                contentAlignment = Alignment.Center
            ) { Text("Set ${com.dhruv.focusguard.ui.sky.durationText(minutes)}", style = gs(40, FontWeight.Medium, Color.White)) }
        }
    }
}

@Composable
private fun SheetRound(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFE9E3FA)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, style = gs(56, FontWeight.Medium, Color(0xFF5149A9))) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskPickerSheet(tasks: List<Task>, onPick: (Task) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFFF7F3FC)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Focus on…", style = gs(40, FontWeight.Medium, Sky.Navy), modifier = Modifier.padding(bottom = 12.dp))
            tasks.take(8).forEach { t ->
                val k = kindOf(t)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(t) }.padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(k.tone.tile), contentAlignment = Alignment.Center) {
                        Icon(k.icon, null, tint = k.tone.icon, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(t.title, style = gs(38, FontWeight.Medium, Sky.Navy), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
