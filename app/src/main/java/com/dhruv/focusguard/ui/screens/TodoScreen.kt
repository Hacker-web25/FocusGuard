package com.dhruv.focusguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.R
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.guard.overlay.Glass
import com.dhruv.focusguard.guard.overlay.gradientTint
import com.dhruv.focusguard.ui.components.rememberProtectionState
import com.dhruv.focusguard.ui.components.openAccessibilitySettings
import com.dhruv.focusguard.ui.focus.FocusMode
import com.dhruv.focusguard.ui.sky.GSans
import com.dhruv.focusguard.ui.sky.MASCOT_X
import com.dhruv.focusguard.ui.sky.MASCOT_Y
import com.dhruv.focusguard.ui.sky.MP
import com.dhruv.focusguard.ui.sky.Mascot
import com.dhruv.focusguard.ui.sky.Sky
import com.dhruv.focusguard.ui.sky.SkyFit
import com.dhruv.focusguard.ui.sky.SkyScaffold
import com.dhruv.focusguard.ui.sky.anchorAt
import com.dhruv.focusguard.ui.sky.at
import com.dhruv.focusguard.ui.sky.baselineAt
import com.dhruv.focusguard.ui.sky.centerAt
import com.dhruv.focusguard.ui.sky.dueLabel
import com.dhruv.focusguard.ui.sky.durationText
import com.dhruv.focusguard.ui.sky.gs
import com.dhruv.focusguard.ui.sky.isHighPriority
import com.dhruv.focusguard.ui.sky.kindOf
import com.dhruv.focusguard.ui.sky.longDate
import com.dhruv.focusguard.ui.sky.mp
import com.dhruv.focusguard.ui.sky.mpx
import com.dhruv.focusguard.ui.sky.sameDay
import com.dhruv.focusguard.ui.sky.sparkle
import com.dhruv.focusguard.ui.sky.startOfDay
import com.dhruv.focusguard.ui.sky.subtitleOf
import com.dhruv.focusguard.ui.sky.tasksForDay
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════
//  To-do: the home screen, built from the mockup. The header is
//  pinned to the top exactly as drawn; the list card runs down to
//  the bottom bar and scrolls.
// ═════════════════════════════════════════════════════════════════

@Composable
fun TodoRoute(
    viewModel: TaskViewModel,
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onFocus: (FocusMode, Long?) -> Unit,
    onGoals: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val active by viewModel.activeTasks.collectAsState()
    val completed by viewModel.completedTasks.collectAsState()
    val protection = rememberProtectionState()
    var day by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var celebrate by remember { mutableIntStateOf(0) }
    val now = remember(active, completed) { System.currentTimeMillis() }
    val list = remember(active, completed, day) { tasksForDay(active, completed, day, now) }
    val todays = remember(active, completed) { tasksForDay(active, completed, now, now) }
    val mood = if (todays.isEmpty()) 0f else todays.count { it.isCompleted } / todays.size.toFloat()

    TodoContent(
        name = viewModel.userName,
        now = now,
        day = day,
        tasks = list,
        mood = mood,
        celebrate = celebrate,
        protectionDown = !protection.running,
        onFixProtection = { context.openAccessibilitySettings() },
        onPickDay = { day = it },
        onToggle = { t ->
            if (!t.isCompleted) celebrate++
            viewModel.toggleComplete(t)
        },
        onOpen = { t -> if (t.isCompleted) onEditTask(t.id) else onFocus(FocusMode.Timer, t.id) },
        onEdit = { t -> onEditTask(t.id) },
        onTimer = { onFocus(FocusMode.Timer, null) },
        onStopwatch = { onFocus(FocusMode.Stopwatch, null) },
        onAdd = onAddTask,
        onGoals = onGoals,
        onSettings = onSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodoContent(
    name: String,
    now: Long,
    day: Long,
    tasks: List<Task>,
    mood: Float,
    celebrate: Int,
    protectionDown: Boolean,
    onFixProtection: () -> Unit,
    onPickDay: (Long) -> Unit,
    onToggle: (Task) -> Unit,
    onOpen: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onTimer: () -> Unit,
    onStopwatch: () -> Unit,
    onAdd: () -> Unit,
    onGoals: () -> Unit,
    onSettings: () -> Unit,
    reference: Boolean = false
) {
    val context = LocalContext.current
    val haptics = remember { runCatching { Haptics(context) }.getOrNull() }
    var pickDate by remember { mutableStateOf(false) }

    SkyScaffold(res = R.drawable.sky_todo, fit = SkyFit.TopWidth) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenH = maxHeight.value / MP              // in mockup px
            val navInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp().value } / MP
            val navCenter = screenH - navInset - 84f
            val cardBottom = navCenter - 60f - 26f

            // ── Greeting ───────────────────────────────────────────
            Leaf(Modifier.at(50, 133).size(48.mp))
            Text(greeting(now), style = gs(30.1f, color = Color.White), modifier = Modifier.baselineAt(114, 155.1f))
            Text(
                name.ifBlank { "there" }, style = gs(60.4f, FontWeight.Bold, Color.White), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.width(430.mp).baselineAt(110, 217.7f)
            )
            Text("Small steps today,", style = gs(24.5f, color = Color.White.copy(alpha = 0.95f)), modifier = Modifier.baselineAt(114, 274.5f))
            Text("big dreams tomorrow.", style = gs(24.5f, color = Color.White.copy(alpha = 0.95f)), modifier = Modifier.baselineAt(115, 306.6f))
            Canvas(Modifier.at(356, 285).size(26.mp)) { sparkle(center, size.minDimension * 0.46f, Color(0xFFFFF0BF)) }

            Mascot(mood = mood, celebrate = celebrate, haptics = haptics, reference = reference, modifier = Modifier.at(MASCOT_X, MASCOT_Y))

            if (protectionDown) {
                ProtectionPill(onFixProtection, Modifier.at(50, 72))
            }

            // ── The list card ──────────────────────────────────────
            Glass(
                modifier = Modifier.at(35, 388).size(957.mp, (cardBottom - 388f).coerceAtLeast(400f).mp),
                shape = RoundedCornerShape(45.mp),
                tint = Sky.CardTint.copy(alpha = 0.86f),
                rim = Color.White.copy(alpha = 0.95f),
                elevation = 6.dp
            ) {
                Box(Modifier.fillMaxSize()) {
                    // card origin = mockup (35, 388)
                    val done = tasks.count { it.isCompleted }
                    Text("To-do List", style = gs(44.8f, FontWeight.Medium, Color(0xFF091E4E)), modifier = Modifier.baselineAt(87 - 35, 460.1f - 388))
                    Text(
                        "${tasks.size} ${if (tasks.size == 1) "task" else "tasks"} • $done completed",
                        style = gs(25.2f, color = Sky.Muted), modifier = Modifier.baselineAt(88 - 35, 500.5f - 388)
                    )
                    BigDateChip(day, now, onClick = { pickDate = true }, modifier = Modifier.anchorAt(912 - 35, 414 - 388, 1f, 0f))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Sky.Chevron, modifier = Modifier.centerAt(938 - 35, 447 - 388).size(34.mp))

                    Row(Modifier.anchorAt(912 - 35, 492 - 388, 1f, 0f), horizontalArrangement = Arrangement.spacedBy(12.mp)) {
                        ModeButton(Icons.Outlined.HourglassEmpty, "Timer", onClick = { haptics?.click(); onTimer() })
                        ModeButton(Icons.Outlined.Timer, "Stopwatch", onClick = { haptics?.click(); onStopwatch() })
                    }

                    if (tasks.isEmpty()) {
                        EmptyDay(onAdd, Modifier.at(0, 600 - 388).fillMaxWidth())
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .at(0, 560 - 388 - 12)
                                .fillMaxWidth()
                                .height(((cardBottom - 560f + 12f) - 14f).coerceAtLeast(100f).mp),
                            contentPadding = PaddingValues(top = 12.mp, bottom = 24.mp),
                            verticalArrangement = Arrangement.spacedBy(21.mp)
                        ) {
                            items(tasks, key = { it.id }) { t ->
                                Box(Modifier.animateItemPlacement(tween(380, easing = FastOutSlowInEasing)).padding(start = 21.mp)) {
                                    TaskRow(
                                        task = t, now = now, haptics = haptics,
                                        onToggle = { onToggle(t) }, onOpen = { onOpen(t) }, onEdit = { onEdit(t) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Bottom bar ─────────────────────────────────────────
            BottomBar(
                onGoals = { haptics?.tick(); onGoals() },
                onSettings = { haptics?.tick(); onSettings() },
                onAdd = { haptics?.click(); onAdd() },
                modifier = Modifier.centerAt(512, navCenter)
            )
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMidnight(day))
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPickDay(fromUtcMidnight(it)) }
                    pickDate = false
                }) { Text("Show this day") }
            },
            dismissButton = {
                TextButton(onClick = { onPickDay(System.currentTimeMillis()); pickDate = false }) { Text("Today") }
            }
        ) { DatePicker(state = state) }
    }
}

private fun greeting(now: Long): String {
    val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> "Good morning,"
        in 12..16 -> "Good afternoon,"
        in 17..21 -> "Good evening,"
        else -> "Hello,"
    }
}

// The date picker speaks UTC midnights.
private fun utcMidnight(local: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun fromUtcMidnight(utc: Long): Long {
    val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0)
    }.timeInMillis
}

// ── Header pieces ──────────────────────────────────────────────────

@Composable
private fun Leaf(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val base = Offset(w * 0.12f, h * 0.92f)
        val tip = Offset(w * 0.94f, h * 0.06f)
        val p = Path().apply {
            moveTo(base.x, base.y)
            cubicTo(w * 0.02f, h * 0.45f, w * 0.45f, h * 0.05f, tip.x, tip.y)
            cubicTo(w * 0.95f, h * 0.55f, w * 0.55f, h * 0.95f, base.x, base.y)
            close()
        }
        drawPath(p, Brush.linearGradient(listOf(Color(0xFFB7EBCB), Color(0xFF79C3A6), Color(0xFF5FA79E)), start = tip, end = base))
        drawLine(Color.White.copy(alpha = 0.75f), Offset(w * 0.20f, h * 0.82f), Offset(w * 0.80f, h * 0.20f), strokeWidth = w * 0.045f, cap = StrokeCap.Round)
    }
}

@Composable
private fun ProtectionPill(onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .height(48.mp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE2304F).copy(alpha = 0.88f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.mp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Color.White, modifier = Modifier.size(26.mp))
        Spacer(Modifier.width(8.mp))
        Text("Instagram guard is off — tap to fix", style = gs(20, FontWeight.Medium, Color.White))
    }
}

/** The date, bigger than the mockup's so it reads first. Tap to look at another day. */
@Composable
private fun BigDateChip(day: Long, now: Long, onClick: () -> Unit, modifier: Modifier) {
    val label = if (sameDay(day, now)) longDate(day) else longDate(day)
    Row(
        modifier
            .height(66.mp)
            .clip(RoundedCornerShape(50))
            .background(Sky.ChipLav)
            .clickable(role = Role.Button, onClickLabel = "Change day", onClick = onClick)
            .padding(start = 24.mp, end = 26.mp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.CalendarToday, null, tint = Sky.ChipLavInk, modifier = Modifier.size(31.mp))
        Spacer(Modifier.width(16.mp))
        Text(label, style = gs(27, FontWeight.Medium, Sky.ChipLavInk))
    }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.93f else 1f, spring(0.5f, 800f), label = "mode")
    Row(
        Modifier
            .scale(s)
            .height(52.mp)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.9f), Color(0xFFF1EBFD))))
            .drawBehind {
                drawRoundRect(Color(0xFFD9CEF8), style = Stroke(1.5.mp.toPx()), cornerRadius = CornerRadius(size.height / 2))
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(start = 16.mp, end = 20.mp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null, tint = Color.White,
            modifier = Modifier.size(28.mp).gradientTint(Brush.linearGradient(listOf(Color(0xFF8E7BFF), Color(0xFFC07BEF))))
        )
        Spacer(Modifier.width(9.mp))
        Text(label, style = gs(21, FontWeight.Medium, Sky.ChipLavInk))
    }
}

@Composable
private fun EmptyDay(onAdd: () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("A clear day", style = gs(36, FontWeight.Medium, Sky.Navy))
        Spacer(Modifier.height(10.mp))
        Text("Add one small thing to begin.", style = gs(24, color = Sky.Sub))
        Spacer(Modifier.height(30.mp))
        Row(
            Modifier.height(72.mp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(*Sky.PauseStops))
                .clickable(role = Role.Button, onClick = onAdd).padding(horizontal = 34.mp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(32.mp))
            Spacer(Modifier.width(8.mp))
            Text("Add a task", style = gs(25, FontWeight.Medium, Color.White))
        }
    }
}

// ── A task row ─────────────────────────────────────────────────────

/**
 * One row, measured off the mockup: coloured spine down the left edge,
 * check circle, icon tile, title and line, priority chip, and on the right
 * the due date and planned focus time. Tap to focus on it, long-press to edit.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(task: Task, now: Long, haptics: Haptics?, onToggle: () -> Unit, onOpen: () -> Unit, onEdit: () -> Unit) {
    val kind = kindOf(task)
    val tone = kind.tone
    val chip = task.isHighPriority && !task.isCompleted
    val h = if (chip) 165 else 152
    val fade by animateFloatAsState(if (task.isCompleted) 0.62f else 1f, tween(400), label = "fade")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.985f else 1f, spring(0.6f, 900f), label = "row")

    Box(
        Modifier
            .size(914.mp, h.mp)
            .scale(s)
            .drawBehind {
                val r = CornerRadius(30.mp.toPx())
                // soft lavender shadow under the row
                drawRoundRect(Color(0xFF8C7FC4).copy(alpha = 0.10f), topLeft = Offset(0f, 3.mp.toPx()), size = size, cornerRadius = r)
                val fill = if (chip) Color(0xFFF9ECF2) else Sky.RowFill
                drawRoundRect(fill, cornerRadius = r)
                drawRoundRect(Brush.horizontalGradient(listOf(tone.spine.copy(alpha = if (chip) 0.06f else 0.035f), Color.Transparent)), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = 0.9f), cornerRadius = r, style = Stroke(1.6f.mp.toPx()))
                // the spine: a left border that follows the rounded corners
                val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
                val inner = Path().apply { addRoundRect(RoundRect(6.mp.toPx(), 0f, size.width + 6.mp.toPx(), size.height, r)) }
                val spine = Path().apply { op(outer, inner, PathOperation.Difference) }
                drawPath(spine, tone.spine.copy(alpha = fade))
            }
            .graphicsLayer { alpha = fade.coerceAtLeast(0.75f) }
            .combinedClickable(
                interactionSource = source, indication = null,
                onClick = { haptics?.tick(); onOpen() },
                onLongClick = { haptics?.click(); onEdit() },
                onClickLabel = "Focus on this", onLongClickLabel = "Edit"
            )
    ) {
        // row origin = mockup (56, top)
        CheckCircle(
            done = task.isCompleted,
            onToggle = { haptics?.click(); onToggle() },
            modifier = Modifier.centerAt(105 - 56, h / 2f - 4f)
        )
        val tileY = if (chip) 67f else 60f
        Box(Modifier.centerAt(190 - 56, tileY).size(70.mp).clip(CircleShape).background(tone.tile)) {
            Icon(kind.icon, null, tint = tone.icon, modifier = Modifier.align(Alignment.Center).size(38.mp))
        }
        Text(
            task.title,
            style = gs(26, FontWeight.Medium, if (task.isCompleted) Color(0xFF8A8EB8) else Color(0xFF060F4C))
                .copy(textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(455.mp).baselineAt(247 - 56, 51.2f)
        )
        Text(
            subtitleOf(task), style = gs(21, color = Color(0xFF7479AD)), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(455.mp).baselineAt(247 - 56, 85.4f)
        )
        if (chip) PriorityChip(Modifier.at(246 - 56, 640 - 536), height = 36, textSize = 18f)

        // Right column: due date, focus time
        val dl = task.deadline
        val dur = durationText(task.durationMinutes)
        val chips = listOfNotNull(
            dl?.let { Triple(Icons.Outlined.CalendarToday, dueLabel(it, now), true) },
            dur.takeIf { it.isNotEmpty() }?.let { Triple(Icons.Outlined.Timer, it, false) }
        )
        if (chips.isNotEmpty()) {
            Column(Modifier.anchorAt(721 - 56, h / 2f, 0f, 0.5f), verticalArrangement = Arrangement.spacedBy(8.mp)) {
                chips.forEach { (icon, text, isDate) ->
                    val isToday = isDate && text == "Today"
                    InfoChip(
                        icon = icon,
                        text = text,
                        tint = if (isDate) tone.dateInk else tone.durationInk,
                        iconTint = if (isDate) tone.dateInk else tone.durationInk,
                        bg = tone.chip,
                        size = if (isDate) (if (isToday) 19.9f else 19.4f) else 22.7f,
                        weight = if (isDate && !isToday) FontWeight.Normal else FontWeight.Medium
                    )
                }
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Sky.Chevron, modifier = Modifier.centerAt(937 - 56, h / 2f - 4f).size(40.mp))
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, tint: Color, iconTint: Color, bg: Color, size: Float, weight: FontWeight) {
    Row(
        Modifier
            .height(50.mp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(start = 21.mp, end = 22.mp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(26.mp))
        Spacer(Modifier.width(14.mp))
        Text(text, style = gs(size, weight, tint))
    }
}

/** The tick circle: fills, draws a tick and throws a few sparks when done. */
@Composable
private fun CheckCircle(done: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val fill = remember { Animatable(if (done) 1f else 0f) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done && fill.value < 1f) {
            launch { burst.snapTo(0f); burst.animateTo(1f, tween(520)) }
            fill.animateTo(1f, spring(0.55f, 380f))
        } else if (!done) fill.animateTo(0f, tween(200))
    }
    Canvas(
        modifier
            .size(64.mp)   // generous touch target around the 37px circle
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox, onClick = onToggle)
    ) {
        val c = center
        val r = mpx(18.5f)
        val f = fill.value
        if (burst.value < 1f) {
            for (i in 0 until 8) {
                val a = i * 0.785f
                val d = r * (1.2f + burst.value * 0.9f)
                drawCircle(Color(0xFFB79CFF).copy(alpha = 1f - burst.value), radius = mpx(3f), center = Offset(c.x + cos(a) * d, c.y + sin(a) * d))
            }
        }
        if (f > 0f) {
            drawCircle(Brush.linearGradient(listOf(Color(0xFF9C8CFF), Color(0xFFC291F5))), radius = r * (0.6f + 0.4f * f.coerceAtMost(1f)), center = c, alpha = f.coerceIn(0f, 1f))
        }
        drawCircle(if (f > 0.5f) Color.White.copy(alpha = 0.9f) else Sky.CheckRing, radius = r - mpx(1.5f), center = c, style = Stroke(mpx(3f)))
        if (f > 0.05f) {
            val t = f.coerceIn(0f, 1f)
            val a = Offset(c.x - r * 0.42f, c.y + r * 0.02f)
            val m = Offset(c.x - r * 0.10f, c.y + r * 0.32f)
            val e = Offset(c.x + r * 0.45f, c.y - r * 0.30f)
            val p = Path().apply {
                moveTo(a.x, a.y)
                if (t < 0.4f) lineTo(a.x + (m.x - a.x) * t / 0.4f, a.y + (m.y - a.y) * t / 0.4f)
                else { lineTo(m.x, m.y); val k = (t - 0.4f) / 0.6f; lineTo(m.x + (e.x - m.x) * k, m.y + (e.y - m.y) * k) }
            }
            drawPath(p, Color.White, style = Stroke(mpx(3.6f), cap = StrokeCap.Round))
        }
    }
}

// ── Bottom bar ─────────────────────────────────────────────────────

@Composable
private fun BottomBar(onGoals: () -> Unit, onSettings: () -> Unit, onAdd: () -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Glass(
            modifier = Modifier.size(470.mp, 120.mp),
            shape = RoundedCornerShape(50),
            tint = Color.White.copy(alpha = 0.62f),
            rim = Color.White.copy(alpha = 0.9f),
            elevation = 6.dp
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                NavItem(Icons.Rounded.CheckCircle, "Tasks", selected = true) {}
                NavItem(Icons.Rounded.Star, "Goals", selected = false, onClick = onGoals)
                NavItem(Icons.Rounded.Settings, "Settings", selected = false, onClick = onSettings)
            }
        }
        Spacer(Modifier.width(26.mp))
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val s by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.45f, 700f), label = "add")
        Box(
            Modifier
                .scale(s)
                .size(120.mp)
                .drawBehind {
                    drawCircle(Color(0xFF9B7CF6).copy(alpha = 0.28f), radius = size.minDimension * 0.56f, center = center + Offset(0f, 6.mp.toPx()))
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFA5A7FE), Color(0xFF9A8EFC), Color(0xFFD5A9F8))))
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = "Add a task", onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, "Add a task", tint = Color.White, modifier = Modifier.size(54.mp))
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(30.mp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 22.mp, vertical = 10.mp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (selected) Color(0xFF7A6CF0) else Color(0xFF9A9CC4), modifier = Modifier.size(42.mp))
        Spacer(Modifier.height(4.mp))
        Text(label, style = gs(18, FontWeight.Medium, if (selected) Color(0xFF5149A9) else Color(0xFF8D90B8)))
    }
}
