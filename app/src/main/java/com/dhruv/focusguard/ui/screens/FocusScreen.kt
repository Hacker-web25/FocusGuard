package com.dhruv.focusguard.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.R
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.guard.overlay.FallingPetals
import com.dhruv.focusguard.guard.overlay.Glass
import com.dhruv.focusguard.ui.focus.FocusClock
import com.dhruv.focusguard.ui.focus.FocusMode
import com.dhruv.focusguard.ui.focus.FocusPhase
import com.dhruv.focusguard.ui.focus.FocusSession
import com.dhruv.focusguard.ui.focus.clockText
import com.dhruv.focusguard.ui.sky.AudienceStage
import com.dhruv.focusguard.ui.sky.GSans
import com.dhruv.focusguard.ui.sky.Sky
import com.dhruv.focusguard.ui.sky.SkyFit
import com.dhruv.focusguard.ui.sky.SkyMotion
import com.dhruv.focusguard.ui.sky.SkyScaffold
import com.dhruv.focusguard.ui.sky.StagePhase
import com.dhruv.focusguard.ui.sky.calloutFor
import com.dhruv.focusguard.ui.sky.clockPoint
import com.dhruv.focusguard.ui.sky.durationText
import com.dhruv.focusguard.ui.sky.isHighPriority
import com.dhruv.focusguard.ui.sky.kindOf
import com.dhruv.focusguard.ui.sky.longDate
import com.dhruv.focusguard.ui.sky.sparkle
import com.dhruv.focusguard.ui.sky.subtitleOf
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════
//  Focus: one screen for the timer and the stopwatch.
//
//  The ring and the crowd repaint every frame, but nothing above
//  them recomposes: the moving numbers live in plain float state
//  that only the drawing reads, and the digits change once a second.
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

    val now = remember(active, completed) { System.currentTimeMillis() }
    val dayDone = remember(active, completed) {
        val all = active.size + completed.count { it.completedAt?.let { c -> c > now - 86_400_000L } == true }
        if (all == 0) 0f else completed.count { it.completedAt?.let { c -> c > now - 86_400_000L } == true } / all.toFloat()
    }

    FocusContent(
        session = s,
        task = task,
        dayProgress = dayDone,
        dateMillis = now,
        pickable = active,
        onBack = onBack,
        onChange = { change -> FocusClock.update(context, change) },
        onOpenTask = { t -> onEditTask(t.id) },
        onPickTask = { t ->
            FocusClock.update(context) {
                it.copy(taskId = t.id).let { n ->
                    if (t.durationMinutes > 0 && n.mode == FocusMode.Timer) n.withTarget(t.durationMinutes * 60_000L) else n
                }
            }
        },
        onMarkDone = { t -> if (!t.isCompleted) viewModel.toggleComplete(t) }
    )
}

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

    // Smooth values for the drawing: written every frame, read only inside
    // Canvas draw lambdas, so the ring is fluid without recomposing anything.
    val ringProgress = remember { mutableFloatStateOf(session.progress(clock())) }
    val beat = remember { mutableFloatStateOf(0f) }

    // Values the composition reads: each changes at most once a second.
    var phase by remember { mutableStateOf(session.phase(clock())) }
    var timeText by remember { mutableStateOf("") }
    var cheer by remember { mutableIntStateOf(0) }
    var wave by remember { mutableIntStateOf(0) }
    var dialling by remember { mutableStateOf(false) }
    var lastSecond by remember { mutableIntStateOf(-1) }

    val live by rememberUpdatedState(session)
    val dayNow by rememberUpdatedState(dayProgress)
    val refNow by rememberUpdatedState(reference)

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { frame ->
                val dt = if (last == 0L) 0.016f else ((frame - last) / 1e9f).coerceIn(0f, 0.05f)
                last = frame
                beat.floatValue += dt
                val s = live.value
                val t = clock()
                val target = if (refNow.value) 0.111f else s.progress(t)
                // Ease toward the true progress: the arc never jumps, even when
                // the session is paused, reset or dialled to a new length.
                val p = ringProgress.floatValue
                ringProgress.floatValue = p + (target - p) * (1f - kotlin.math.exp(-9f * dt))

                val ph = s.phase(t)
                if (ph != phase) phase = ph
                val secs = if (s.mode == FocusMode.Timer) ceil(s.remaining(t) / 1000.0).toLong()
                else floor(s.elapsed(t) / 1000.0).toLong()
                val txt = clockText(secs * 1000L, forceHours = s.mode == FocusMode.Timer && s.targetMs >= 3_600_000L)
                if (txt != timeText) timeText = txt
                lastSecond = secs.toInt()

                val c = when (ph) {
                    FocusPhase.Finished -> 5
                    FocusPhase.Idle -> (dayNow.value * 5).roundToInt()
                    else -> ceil(target * 5f).toInt().coerceIn(if (ph == FocusPhase.Running) 1 else 0, 5)
                }
                if (c != cheer) cheer = c
                val w = floor(target * 4f).toInt()
                if (w != wave && ph == FocusPhase.Running) wave = w
            }
            if (!SkyMotion.enabled) break
        }
    }

    // Ticking down the last ten seconds, and a flourish at zero.
    LaunchedEffect(lastSecond, phase, session.mode) {
        if (phase == FocusPhase.Running && session.mode == FocusMode.Timer) {
            when (lastSecond) {
                in 1..3 -> haptics?.click()
                in 4..10 -> haptics?.tick()
            }
        }
    }

    LaunchedEffect(session) {
        if (session.mode == FocusMode.Timer && session.running) {
            delay(session.remaining(clock()))
            onChange { it.finish(clock()) }
            haptics?.success()
        }
    }

    val view = LocalView.current
    DisposableEffect(session.running) {
        view.keepScreenOn = session.running
        onDispose { view.keepScreenOn = false }
    }

    val stagePhase = when (phase) {
        FocusPhase.Idle -> StagePhase.Idle
        FocusPhase.Running -> StagePhase.Running
        FocusPhase.Paused -> StagePhase.Paused
        FocusPhase.Finished -> StagePhase.Finished
    }
    val shown = ringProgress.floatValue
    val callout = when {
        reference -> "Almost there!"
        session.mode == FocusMode.Stopwatch && phase == FocusPhase.Running && shown >= 1f -> "Goal reached!"
        else -> calloutFor(stagePhase, shown)
    }

    var showDurations by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val canSet = session.mode == FocusMode.Timer && phase == FocusPhase.Idle

    SkyScaffold(res = R.drawable.sky_focus, fit = SkyFit.Cover) {
        if (SkyMotion.enabled) FallingPetals()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top row ────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onBack)
                Spacer(Modifier.weight(1f))
                DateChip(dateMillis)
            }

            Spacer(Modifier.height(6.dp))

            TaskBanner(
                task = task,
                mode = session.mode,
                onClick = {
                    if (task != null) onOpenTask(task)
                    else if (pickable.isNotEmpty()) showPicker = true
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.weight(0.6f))

            // ── The ring ───────────────────────────────────────────
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val ringSize = minOf(maxWidth - 72.dp, 290.dp)
                TimerRing(
                    session = session,
                    phase = phase,
                    progress = ringProgress,
                    beat = beat,
                    timeText = if (reference) "1:30:00" else timeText,
                    canSet = canSet,
                    dialling = dialling,
                    onDialling = { dialling = it },
                    haptics = haptics,
                    onSetTarget = { ms -> onChange { it.withTarget(ms) } },
                    onTapTime = { if (canSet) showDurations = true },
                    modifier = Modifier.size(ringSize)
                )
            }

            Spacer(Modifier.weight(0.45f))

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
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(10.dp))
            ResetPill(
                enabled = phase != FocusPhase.Idle,
                onClick = { haptics?.tick(); onChange { it.reset() } },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.weight(0.5f))

            // ── The crowd ──────────────────────────────────────────
            AudienceStage(
                phase = stagePhase,
                cheer = cheer,
                wave = if (phase == FocusPhase.Running) wave else 0,
                callout = callout,
                haptics = haptics,
                referenceFaces = reference,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
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
private fun BackButton(onClick: () -> Unit) {
    Glass(
        modifier = Modifier
            .size(38.dp)
            .clickable(role = Role.Button, onClickLabel = "Back", onClick = onClick),
        shape = CircleShape,
        tint = Color.White.copy(alpha = 0.40f),
        rim = Color.White.copy(alpha = 0.85f),
        elevation = 2.dp
    ) {
        Icon(
            Icons.Rounded.ArrowBackIosNew, contentDescription = "Back", tint = Color.White,
            modifier = Modifier.align(Alignment.Center).padding(start = 3.dp).size(15.dp)
        )
    }
}

@Composable
private fun DateChip(dateMillis: Long) {
    Glass(
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(50),
        tint = Color.White.copy(alpha = 0.55f),
        rim = Color.White.copy(alpha = 0.8f),
        elevation = 2.dp
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CalendarToday, null, tint = Sky.GlassChipInk, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(longDate(dateMillis), fontFamily = GSans, fontSize = 12.sp, color = Sky.GlassChipInk)
        }
    }
}

@Composable
private fun TaskBanner(task: Task?, mode: FocusMode, onClick: () -> Unit, modifier: Modifier) {
    val kind = task?.let { kindOf(it) }
    Glass(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        tint = Color.White.copy(alpha = 0.58f),
        rim = Color.White.copy(alpha = 0.85f),
        elevation = 3.dp
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFE6E1FE), Color(0xFFD7CFFB)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(kind?.icon ?: Icons.Outlined.Timer, null, tint = Color(0xFF5D50C5), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task?.title ?: if (mode == FocusMode.Timer) "Free focus" else "Stopwatch",
                    fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color(0xFF071859),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    task?.let { subtitleOf(it) } ?: "Tap to pick a task",
                    fontFamily = GSans, fontSize = 12.sp, color = Color(0xFF6B70A8),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (task?.isHighPriority == true) {
                Spacer(Modifier.width(8.dp))
                PriorityChip()
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF8286B5), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun PriorityChip(modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(22.dp)
            .clip(RoundedCornerShape(50))
            .background(Sky.RedChip)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.dhruv.focusguard.ui.sky.Flame(Modifier.size(9.dp, 11.dp))
        Spacer(Modifier.width(4.dp))
        Text("High", fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Sky.Red)
    }
}

/**
 * The glass ring. The arc, its knob and the glow are painted straight from
 * [progress], which a frame loop writes — so it turns smoothly whatever else
 * the screen is doing. While the timer is idle, dragging round the ring sets
 * its length (one lap is an hour) with a tick per minute.
 */
@Composable
private fun TimerRing(
    session: FocusSession,
    phase: FocusPhase,
    progress: MutableFloatState,
    beat: MutableFloatState,
    timeText: String,
    canSet: Boolean,
    dialling: Boolean,
    onDialling: (Boolean) -> Unit,
    haptics: Haptics?,
    onSetTarget: (Long) -> Unit,
    onTapTime: () -> Unit,
    modifier: Modifier
) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(phase) {
        if (phase == FocusPhase.Running || phase == FocusPhase.Finished) {
            pop.animateTo(1.03f, tween(140)); pop.animateTo(1f, spring(0.35f, 300f))
        }
    }
    val density = LocalDensity.current

    Box(modifier.scale(pop.value), contentAlignment = Alignment.Center) {
        Glass(
            modifier = Modifier.fillMaxSize().padding(14.dp),
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
                        val rr = (down.position - c).getDistance() / (size.minDimension / 2f)
                        if (rr < 0.60f) return@awaitEachGesture      // the middle is for tapping
                        fun angle(pos: Offset): Float {
                            val d = pos - c
                            var a = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).toFloat() + 90f
                            if (a < 0) a += 360f
                            return a
                        }
                        onDialling(true)
                        var lastA = angle(down.position)
                        var total = (session.targetMs / 60_000L).toFloat()
                        var lastWhole = total.roundToInt()
                        haptics?.click()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val a = angle(ch.position)
                            var d = a - lastA
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            lastA = a
                            total = (total + d / 6f).coerceIn(1f, 240f)   // 6° a minute
                            val m = total.roundToInt()
                            if (m != lastWhole) {
                                lastWhole = m
                                if (m % 5 == 0) haptics?.click() else haptics?.tick()
                                onSetTarget(m * 60_000L)
                            }
                            ch.consume()
                        }
                        onDialling(false)
                        haptics?.thud()
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    enabled = canSet, onClickLabel = "Choose how long", onClick = onTapTime
                )
        ) {
            val c = center
            val outer = size.minDimension / 2f
            val band = 13.dp.toPx()
            val rr = outer - band / 2f - 1.dp.toPx()
            val t = beat.floatValue
            val shown = progress.floatValue.coerceIn(0f, 1f)

            // Light around the ring, breathing while it runs
            val breathe = if (phase == FocusPhase.Running) 0.07f * sin(t * 2.2f) else 0f
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Transparent, 0.74f to Color.Transparent,
                    0.86f to Color.White.copy(alpha = 0.50f + breathe), 1f to Color.Transparent,
                    center = c, radius = outer
                ),
                radius = outer, center = c
            )
            // Inner disk
            drawCircle(Sky.Disk.copy(alpha = 0.80f), radius = rr - band / 2f - 2.dp.toPx(), center = c)

            // Track
            val arcTopLeft = Offset(c.x - rr, c.y - rr)
            val arcSize = Size(rr * 2, rr * 2)
            drawArc(
                Color(0xFFE8DCFB), 0f, 360f, false,
                topLeft = arcTopLeft, size = arcSize, style = Stroke(band)
            )
            drawCircle(Color.White.copy(alpha = 0.8f), radius = rr + band / 2f, center = c, style = Stroke(2.dp.toPx()))

            // Progress. The gradient is fixed to the dial, not to the sweep, so
            // the colours stay put as the arc grows instead of crawling.
            val sweep = 360f * shown
            if (sweep > 0.4f) {
                rotate(-90f, c) {
                    drawArc(
                        Brush.sweepGradient(
                            0f to Sky.ArcFrom, 0.33f to Sky.ArcTo, 0.66f to Sky.TrailPink, 1f to Sky.ArcFrom,
                            center = c
                        ),
                        0f, sweep, false,
                        topLeft = arcTopLeft, size = arcSize,
                        style = Stroke(band, cap = StrokeCap.Round)
                    )
                }
            }
            val head = clockPoint(c, rr, sweep)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color(0xFFF2C6F7).copy(alpha = 0.45f), Color.Transparent), center = head, radius = band * 1.6f),
                radius = band * 1.6f, center = head
            )
            drawCircle(Color(0xFFC79BEF).copy(alpha = 0.35f), radius = band * 0.62f, center = head + Offset(0f, 1.5f.dp.toPx()))
            drawCircle(Color.White, radius = band * 0.58f, center = head)
            if (phase == FocusPhase.Running) {
                val tw = 0.5f + 0.5f * sin(t * 6f)
                sparkle(clockPoint(c, rr, (sweep - 8f).coerceAtLeast(0f)), band * 0.34f * (0.6f + tw * 0.7f), Color.White.copy(alpha = 0.9f))
            }
            if (dialling) {
                // Minute ticks while you dial it in
                for (i in 0 until 60) {
                    val p = clockPoint(c, rr - band * 0.95f, i * 6f)
                    drawCircle(Color.White.copy(alpha = if (i % 5 == 0) 0.85f else 0.45f), radius = if (i % 5 == 0) 1.8f.dp.toPx() else 1.dp.toPx(), center = p)
                }
            }
        }

        // What it says in the middle
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Timer, null, tint = Color(0xFF414C88), modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    session.mode == FocusMode.Stopwatch -> if (phase == FocusPhase.Idle) "Stopwatch" else "Time focused"
                    phase == FocusPhase.Finished -> "Time's up"
                    phase == FocusPhase.Paused -> "Paused"
                    dialling -> "Set to"
                    else -> "Focus time"
                },
                fontFamily = GSans, fontSize = 13.sp, color = Sky.FocusLabel
            )
            Spacer(Modifier.height(2.dp))
            Text(
                timeText,
                fontFamily = GSans, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, color = Sky.TimerInk,
                fontFeatureSettings = "tnum"
            )
            Spacer(Modifier.height(4.dp))
            val (line1, line2) = when (phase) {
                FocusPhase.Idle -> if (canSet) "Drag the ring" to "or tap to choose" else "Ready" to "Press start"
                FocusPhase.Paused -> "Take a breath" to "Resume when ready"
                FocusPhase.Finished -> "Well done" to "You showed up!"
                FocusPhase.Running -> "Stay focused" to "You got this!"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TargetIcon(Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(line1, fontFamily = GSans, fontSize = 12.sp, color = Sky.StayInk)
            }
            Text(line2, fontFamily = GSans, fontSize = 12.sp, color = Color(0xFF6D74A5))
        }
    }
}

/** A dart in a bullseye. */
@Composable
private fun TargetIcon(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * 0.46f, size.height * 0.54f)
        val r = size.minDimension * 0.42f
        val ink = Sky.Target
        drawCircle(ink, radius = r, center = c, style = Stroke(size.minDimension * 0.11f))
        drawCircle(ink, radius = r * 0.5f, center = c, style = Stroke(size.minDimension * 0.11f))
        val tail = Offset(size.width * 0.95f, size.height * 0.08f)
        drawLine(Color.White, c, tail, strokeWidth = size.minDimension * 0.26f, cap = StrokeCap.Round)
        drawLine(ink, c, tail, strokeWidth = size.minDimension * 0.12f, cap = StrokeCap.Round)
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
            .height(50.dp)
            .width(172.dp)
            .scale(s)
            .drawBehind {
                for (i in 1..5) {
                    val g = i * 2.dp.toPx()
                    drawRoundRect(
                        Color(0xFF9B7CF6).copy(alpha = 0.04f),
                        topLeft = Offset(-g * 0.5f, 3.dp.toPx() + g * 0.2f),
                        size = Size(size.width + g, size.height + g * 0.5f),
                        cornerRadius = CornerRadius(size.height / 2 + g)
                    )
                }
            }
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(*Sky.PauseStops))
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent), endY = size.height * 0.55f))
                drawRoundRect(Color.White.copy(alpha = 0.5f), style = Stroke(1.2f.dp.toPx()), cornerRadius = CornerRadius(size.height / 2))
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(targetState = icon to label, label = "primary") { (ic, lb) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(lb, fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun ResetPill(enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Glass(
        modifier = modifier
            .height(36.dp)
            .width(104.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(50),
        tint = Color.White.copy(alpha = 0.40f),
        rim = Color.White.copy(alpha = 0.7f),
        elevation = 0.dp
    ) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.RestartAlt, null, tint = Color(0xFF777CB1), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reset", fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Sky.ResetInk)
        }
    }
}

// ── Sheets ─────────────────────────────────────────────────────────

private val PRESETS = listOf(5, 10, 15, 20, 25, 30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationSheet(current: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableIntStateOf((current / 60_000L).toInt()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFF7F3FC)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Focus for", fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Sky.Navy)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetRound("–") { minutes = (minutes - 1).coerceAtLeast(1) }
                Text(
                    durationText(minutes).ifEmpty { "0m" },
                    fontFamily = GSans, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = Sky.TimerInk,
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
                SheetRound("+") { minutes = (minutes + 1).coerceAtMost(240) }
            }
            Spacer(Modifier.height(14.dp))
            PRESETS.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        val sel = m == minutes
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50))
                                .background(if (sel) Color(0xFF9A90FB) else Color(0xFFE9E3FA))
                                .clickable { minutes = m },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(durationText(m), fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (sel) Color.White else Color(0xFF5149A9))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(*Sky.PauseStops))
                    .clickable { onPick(minutes * 60_000L) },
                contentAlignment = Alignment.Center
            ) {
                Text("Set ${durationText(minutes)}", fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun SheetRound(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFE9E3FA)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 22.sp, color = Color(0xFF5149A9)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskPickerSheet(tasks: List<Task>, onPick: (Task) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFFF7F3FC)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Focus on…", fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Sky.Navy, modifier = Modifier.padding(bottom = 10.dp))
            tasks.take(8).forEach { t ->
                val k = kindOf(t)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(t) }.padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(k.tone.tile), contentAlignment = Alignment.Center) {
                        Icon(k.icon, null, tint = k.tone.icon, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(t.title, fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Sky.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
