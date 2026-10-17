package com.dhruv.focusguard.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.R
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.guard.overlay.Glass
import com.dhruv.focusguard.ui.components.openAccessibilitySettings
import com.dhruv.focusguard.ui.components.rememberProtectionState
import com.dhruv.focusguard.ui.focus.FocusMode
import com.dhruv.focusguard.ui.sky.Due
import com.dhruv.focusguard.ui.sky.GSans
import com.dhruv.focusguard.ui.sky.Mascot
import com.dhruv.focusguard.ui.sky.Sky
import com.dhruv.focusguard.ui.sky.SkyFit
import com.dhruv.focusguard.ui.sky.SkyScaffold
import com.dhruv.focusguard.ui.sky.Tones
import com.dhruv.focusguard.ui.sky.dueOf
import com.dhruv.focusguard.ui.sky.dueText
import com.dhruv.focusguard.ui.sky.durationText
import com.dhruv.focusguard.ui.sky.isHighPriority
import com.dhruv.focusguard.ui.sky.kindOf
import com.dhruv.focusguard.ui.sky.orderedTasks
import com.dhruv.focusguard.ui.sky.sparkle
import com.dhruv.focusguard.ui.sky.subtitleOf
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════
//  To-do: the home screen. Everything is sized in dp for a phone,
//  and the list holds every task, most pressing first, each saying
//  how long is left.
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
    var celebrate by remember { mutableIntStateOf(0) }
    val now = remember(active, completed) { System.currentTimeMillis() }
    val tasks = remember(active, completed) { orderedTasks(active, completed, now) }
    val mood = if (tasks.isEmpty()) 0f else tasks.count { it.isCompleted } / tasks.size.toFloat()

    TodoContent(
        name = viewModel.userName,
        now = now,
        tasks = tasks,
        mood = mood,
        celebrate = celebrate,
        protectionDown = !protection.running,
        onFixProtection = { context.openAccessibilitySettings() },
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

@Composable
fun TodoContent(
    name: String,
    now: Long,
    tasks: List<Task>,
    mood: Float,
    celebrate: Int,
    protectionDown: Boolean,
    onFixProtection: () -> Unit,
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
    val done = tasks.count { it.isCompleted }

    SkyScaffold(res = R.drawable.sky_todo, fit = SkyFit.TopWidth) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Greeting and the mascot ────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                Column(Modifier.padding(start = 20.dp, top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Leaf(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(greeting(now), fontFamily = GSans, fontSize = 16.sp, color = Color.White.copy(alpha = 0.95f))
                    }
                    Text(
                        name.ifBlank { "there" },
                        fontFamily = GSans, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(210.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Small steps today,\nbig dreams tomorrow. ✦",
                        fontFamily = GSans, fontSize = 14.sp, lineHeight = 19.sp,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
                Mascot(
                    mood = mood, celebrate = celebrate, haptics = haptics, reference = reference,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp)
                        .size(150.dp)
                )
            }

            // ── The list ───────────────────────────────────────────
            Glass(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                shape = RoundedCornerShape(26.dp),
                tint = Sky.CardTint.copy(alpha = 0.86f),
                rim = Color.White.copy(alpha = 0.95f),
                elevation = 6.dp
            ) {
                Column(Modifier.padding(top = 16.dp, bottom = 12.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("To-do List", fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 23.sp, color = Color(0xFF091E4E))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${tasks.size} ${if (tasks.size == 1) "task" else "tasks"} · $done done",
                                fontFamily = GSans, fontSize = 13.sp, color = Sky.Muted
                            )
                        }
                        ModeButton(Icons.Outlined.HourglassEmpty, "Timer") { haptics?.click(); onTimer() }
                        Spacer(Modifier.width(8.dp))
                        ModeButton(Icons.Outlined.Timer, "Stopwatch") { haptics?.click(); onStopwatch() }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (tasks.isEmpty()) {
                        EmptyDay(onAdd, Modifier.fillMaxWidth().padding(vertical = 24.dp))
                    } else {
                        Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            tasks.forEach { t ->
                                TaskRow(
                                    task = t, now = now, haptics = haptics,
                                    onToggle = { onToggle(t) }, onOpen = { onOpen(t) }, onEdit = { onEdit(t) }
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (protectionDown) {
                ProtectionPill(onFixProtection, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
            }

            // ── Bottom bar ─────────────────────────────────────────
            BottomBar(
                onGoals = { haptics?.tick(); onGoals() },
                onSettings = { haptics?.tick(); onSettings() },
                onAdd = { haptics?.click(); onAdd() },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 10.dp)
            )
        }
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
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE2304F).copy(alpha = 0.92f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text("Instagram guard is off — fix", fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color.White)
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
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.92f))
            .drawBehind {
                drawRoundRect(Color(0xFFDCD2F8), style = Stroke(1.dp.toPx()), cornerRadius = CornerRadius(size.height / 2))
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(start = 9.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF7D6CF2), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Sky.ChipLavInk)
    }
}

@Composable
private fun EmptyDay(onAdd: () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nothing on the list", fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Sky.Navy)
        Spacer(Modifier.height(6.dp))
        Text("Add one small thing to begin.", fontFamily = GSans, fontSize = 13.sp, color = Sky.Sub)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(*Sky.PauseStops))
                .clickable(role = Role.Button, onClick = onAdd)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add a task", fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.White)
        }
    }
}

// ── A task row ─────────────────────────────────────────────────────

/**
 * One task: a coloured spine for its kind, a tick circle, its icon, the title
 * and a line under it, then how long is left. Tap to focus on it, long-press
 * to edit.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(task: Task, now: Long, haptics: Haptics?, onToggle: () -> Unit, onOpen: () -> Unit, onEdit: () -> Unit) {
    val kind = kindOf(task)
    val tone = kind.tone
    val due = dueOf(task, now)
    val urgent = !task.isCompleted && (due == Due.Overdue || due == Due.Today)
    val hot = urgent || (task.isHighPriority && !task.isCompleted)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.98f else 1f, spring(0.6f, 900f), label = "row")
    val fade by animateFloatAsState(if (task.isCompleted) 0.6f else 1f, tween(400), label = "fade")
    val spine by animateColorAsState(if (urgent) Tones.Red.spine else tone.spine, tween(300), label = "spine")

    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(s)
            .graphicsLayer { alpha = fade }
            .drawBehind {
                val r = CornerRadius(18.dp.toPx())
                drawRoundRect(Color(0xFF8C7FC4).copy(alpha = 0.10f), topLeft = Offset(0f, 2.dp.toPx()), size = size, cornerRadius = r)
                drawRoundRect(if (hot) Color(0xFFFBEDF2) else Sky.RowFill, cornerRadius = r)
                drawRoundRect(
                    Brush.horizontalGradient(listOf(spine.copy(alpha = if (hot) 0.07f else 0.035f), Color.Transparent)),
                    cornerRadius = r
                )
                drawRoundRect(Color.White.copy(alpha = 0.9f), cornerRadius = r, style = Stroke(1.2f.dp.toPx()))
                // The spine follows the rounded left edge
                val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
                val inner = Path().apply { addRoundRect(RoundRect(4.dp.toPx(), 0f, size.width + 4.dp.toPx(), size.height, r)) }
                drawPath(Path().apply { op(outer, inner, PathOperation.Difference) }, spine)
            }
            .combinedClickable(
                interactionSource = source, indication = null,
                onClick = { haptics?.tick(); onOpen() },
                onLongClick = { haptics?.click(); onEdit() },
                onClickLabel = "Focus on this", onLongClickLabel = "Edit"
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckCircle(done = task.isCompleted, onToggle = { haptics?.click(); onToggle() })
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(34.dp).clip(CircleShape).background(tone.tile), contentAlignment = Alignment.Center) {
                Icon(kind.icon, null, tint = tone.icon, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    color = if (task.isCompleted) Color(0xFF8A8EB8) else Color(0xFF060F4C),
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isHighPriority && !task.isCompleted) {
                        com.dhruv.focusguard.ui.sky.Flame(Modifier.size(9.dp, 11.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        subtitleOf(task),
                        fontFamily = GSans, fontSize = 12.sp, color = Color(0xFF7479AD),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                DueChip(task, now, due, tone.chip, tone.dateInk)
                if (task.durationMinutes > 0) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        durationText(task.durationMinutes),
                        fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = tone.durationInk.copy(alpha = 0.85f)
                    )
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Sky.Chevron, modifier = Modifier.size(20.dp))
        }
    }
}

/** How long is left, coloured by how pressing it is. */
@Composable
private fun DueChip(task: Task, now: Long, due: Due, toneBg: Color, toneInk: Color) {
    val (bg, ink) = when {
        task.isCompleted -> Color(0xFFEDECF5) to Color(0xFF8A8EB8)
        due == Due.Overdue -> Color(0xFFFBD9E0) to Color(0xFFD5324B)
        due == Due.Today -> Color(0xFFFBE2E8) to Color(0xFFE44C5C)
        due == Due.Tomorrow -> Color(0xFFFBEADA) to Color(0xFFB06F1E)
        due == Due.None -> Color(0xFFEDECF5) to Color(0xFF8286B5)
        else -> toneBg to toneInk
    }
    Row(
        Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (task.isCompleted) "Done" else dueText(task, now),
            fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = ink, maxLines = 1
        )
    }
}

/** The tick circle: fills, draws a tick and throws a few sparks when done. */
@Composable
private fun CheckCircle(done: Boolean, onToggle: () -> Unit) {
    val fill = remember { Animatable(if (done) 1f else 0f) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done && fill.value < 1f) {
            launch { burst.snapTo(0f); burst.animateTo(1f, tween(520)) }
            fill.animateTo(1f, spring(0.55f, 380f))
        } else if (!done) fill.animateTo(0f, tween(200))
    }
    Canvas(
        Modifier
            .size(38.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox, onClick = onToggle)
    ) {
        val c = center
        val r = 10.dp.toPx()
        val f = fill.value
        if (burst.value < 1f) {
            for (i in 0 until 8) {
                val a = i * 0.785f
                val d = r * (1.2f + burst.value * 0.9f)
                drawCircle(Color(0xFFB79CFF).copy(alpha = 1f - burst.value), radius = 1.6f.dp.toPx(), center = Offset(c.x + cos(a) * d, c.y + sin(a) * d))
            }
        }
        if (f > 0f) {
            drawCircle(
                Brush.linearGradient(listOf(Color(0xFF9C8CFF), Color(0xFFC291F5))),
                radius = r * (0.6f + 0.4f * f), center = c, alpha = f
            )
        }
        drawCircle(if (f > 0.5f) Color.White.copy(alpha = 0.9f) else Sky.CheckRing, radius = r - 0.8f.dp.toPx(), center = c, style = Stroke(1.7f.dp.toPx()))
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
            drawPath(p, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

// ── Bottom bar ─────────────────────────────────────────────────────

@Composable
private fun BottomBar(onGoals: () -> Unit, onSettings: () -> Unit, onAdd: () -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Glass(
            modifier = Modifier.height(56.dp).width(228.dp),
            shape = RoundedCornerShape(50),
            tint = Color.White.copy(alpha = 0.66f),
            rim = Color.White.copy(alpha = 0.9f),
            elevation = 5.dp
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                NavItem(Icons.Rounded.CheckCircle, "Tasks", selected = true) {}
                NavItem(Icons.Rounded.Star, "Goals", selected = false, onClick = onGoals)
                NavItem(Icons.Rounded.Settings, "Settings", selected = false, onClick = onSettings)
            }
        }
        Spacer(Modifier.width(12.dp))
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val s by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.45f, 700f), label = "add")
        Box(
            Modifier
                .scale(s)
                .size(56.dp)
                .drawBehind {
                    drawCircle(Color(0xFF9B7CF6).copy(alpha = 0.28f), radius = size.minDimension * 0.54f, center = center + Offset(0f, 3.dp.toPx()))
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFA5A7FE), Color(0xFF9A8EFC), Color(0xFFD5A9F8))))
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = "Add a task", onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, "Add a task", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (selected) Color(0xFF7A6CF0) else Color(0xFF9A9CC4), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontFamily = GSans, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = if (selected) Color(0xFF5149A9) else Color(0xFF8D90B8))
    }
}
