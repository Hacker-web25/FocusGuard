package com.dhruv.focusguard.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.GuardEngine
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.guard.PrefsGuardStore
import com.dhruv.focusguard.ui.components.FocusSprintDialog
import com.dhruv.focusguard.ui.components.HeroTaskCard
import com.dhruv.focusguard.ui.components.ListHeading
import com.dhruv.focusguard.ui.components.MindfulCard
import com.dhruv.focusguard.ui.components.PrimaryButton
import com.dhruv.focusguard.ui.components.SecondaryButton
import com.dhruv.focusguard.ui.components.TaskListRow
import com.dhruv.focusguard.ui.components.TodayCard
import com.dhruv.focusguard.ui.components.byUrgency
import com.dhruv.focusguard.ui.components.rememberResumeTick
import com.dhruv.focusguard.ui.theme.PureWhite
import com.dhruv.focusguard.ui.theme.Radius
import com.dhruv.focusguard.ui.theme.Sizes
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import com.dhruv.focusguard.widget.TaskWidget
import com.dhruv.focusguard.widget.TaskWidgetProvider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tasks — the app's landing space, and its main menu.
 *
 * Opens with today's shape (what's done, what Instagram has cost you), then
 * the single most pressing task at full size, then the list. Urgency is
 * carried by colour and weight rather than by shouting, and ticking anything
 * off is meant to feel worth doing.
 */
@Composable
fun TaskListScreen(
    viewModel: TaskViewModel,
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onOpenLimit: () -> Unit = {}
) {
    val c = MaterialTheme.mindful
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val activeTasks by viewModel.activeTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    var showCompleted by remember { mutableStateOf(false) }
    var sprintTask by remember { mutableStateOf<Task?>(null) }

    // Re-read the guard's own counters whenever we come back to the screen.
    val resume = rememberResumeTick()
    val store = remember { PrefsGuardStore(context) }
    val engine = remember { GuardEngine(store) }
    val stats = remember(resume, activeTasks, completedTasks) {
        val now = System.currentTimeMillis()
        DayStats(
            walkedAway = store.walkAwaysToday,
            focusMinutes = store.focusMinutesToday,
            streak = store.streakDays(),
            instagramMinutes = engine.usedTodayMinutes(now)
        )
    }

    val now = remember(resume, activeTasks) { System.currentTimeMillis() }
    val sorted = remember(activeTasks, now) { byUrgency(activeTasks) }
    val hero = sorted.firstOrNull()
    val rest = remember(sorted) { sorted.drop(1) }
    val completedToday = remember(completedTasks) { countCompletedToday(completedTasks) }
    val dayTotal = completedToday + activeTasks.size

    sprintTask?.let { task ->
        FocusSprintDialog(
            task = task,
            haptics = haptics,
            onFinished = { minutes, completed ->
                store.recordFocusMinutes(minutes)
                if (completed) viewModel.toggleComplete(task)
                sprintTask = null
            },
            onDismiss = { sprintTask = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.canvas)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                top = Space.md,
                bottom = Sizes.bottomBarClearance
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            // ── Greeting ───────────────────────────────────────
            item(key = "greeting") {
                Column {
                    Text(
                        dateLine(),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.2.sp,
                        color = c.textSecondary
                    )
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        greeting(viewModel.userName),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                }
            }

            // ── Today at a glance, plus the Instagram budget ────
            item(key = "today") {
                TodayCard(
                    done = completedToday,
                    total = dayTotal,
                    walkedAway = stats.walkedAway,
                    focusMinutes = stats.focusMinutes,
                    instagramMinutes = stats.instagramMinutes,
                    limitMinutes = viewModel.dailyLimitMinutes,
                    streak = stats.streak,
                    onOpenLimit = onOpenLimit
                )
            }

            // ── Put the list on the home screen (until placed or dismissed) ──
            item(key = "widgetPromo") {
                WidgetPromo(viewModel)
            }

            // ── The one that matters most ──────────────────────
            item(key = "hero") {
                HeroTaskCard(
                    task = hero,
                    now = now,
                    haptics = haptics,
                    onOpen = { hero?.let { onEditTask(it.id) } },
                    onComplete = { hero?.let { viewModel.toggleComplete(it) } },
                    onStartFocus = { hero?.let { sprintTask = it } },
                    onAdd = onAddTask
                )
            }

            // ── The rest of the list ───────────────────────────
            if (rest.isNotEmpty()) {
                item(key = "listHeading") {
                    Spacer(Modifier.height(Space.xxs))
                    ListHeading("Then", "${rest.size} more")
                }
                items(rest, key = { "task-${it.id}" }) { task ->
                    TaskListRow(
                        task = task,
                        now = now,
                        haptics = haptics,
                        onToggle = { viewModel.toggleComplete(task) },
                        onClick = { onEditTask(task.id) }
                    )
                }
            }

            // ── Completed ──────────────────────────────────────
            if (completedTasks.isNotEmpty()) {
                item(key = "completedToggle") {
                    Spacer(Modifier.height(Space.xxs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Radius.field)
                            .clickable {
                                haptics.tick()
                                showCompleted = !showCompleted
                            }
                            .padding(vertical = Space.xs, horizontal = Space.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.titleSmall,
                            color = c.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${completedTasks.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                        Spacer(Modifier.width(Space.xxs))
                        Icon(
                            imageVector = if (showCompleted) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (showCompleted) "Hide completed tasks"
                            else "Show completed tasks",
                            tint = c.textSecondary,
                            modifier = Modifier.size(Sizes.icon)
                        )
                    }
                }

                if (showCompleted) {
                    items(completedTasks, key = { "done-${it.id}" }) { task ->
                        TaskListRow(
                            task = task,
                            now = now,
                            haptics = haptics,
                            onToggle = { viewModel.toggleComplete(task) },
                            onClick = { onEditTask(task.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                haptics.click()
                onAddTask()
            },
            containerColor = c.accentDeep,
            contentColor = PureWhite,
            shape = Radius.button,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Space.gutter, bottom = Space.lg)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add task")
        }
    }
}

/** The guard's own numbers for today, read together so they stay in step. */
private data class DayStats(
    val walkedAway: Int,
    val focusMinutes: Int,
    val streak: Int,
    val instagramMinutes: Int
)

// ── Helpers ────────────────────────────────────────────────────

/** Time-of-day greeting, with the user's name once we know it. */
private fun greeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val base = when {
        hour < 5 -> "Still up"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "Winding down"
    }
    return if (name.isBlank()) base else "$base, $name"
}

private fun dateLine(): String =
    SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())

/** Human deadline label: Today / Tomorrow / Mon, Jan 5. */
internal fun deadlineLabel(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }

    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "Today"

    now.add(Calendar.DAY_OF_YEAR, 1)
    val isTomorrow = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (isTomorrow) return "Tomorrow"

    return SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(millis))
}

/** Tasks the user actually finished today, from real completion timestamps. */
private fun countCompletedToday(tasks: List<Task>): Int {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return tasks.count { (it.completedAt ?: 0L) >= start }
}

// ── Home-screen widget card ────────────────────────────────────

/**
 * Offers the home-screen widget until one is placed or the card is closed.
 * Uses the system's "add widget" prompt where the launcher supports it, and
 * always shows the manual route, since some launchers (MIUI, some ColorOS
 * builds) say they support it and then show nothing.
 */
@Composable
private fun WidgetPromo(viewModel: TaskViewModel) {
    val c = MaterialTheme.mindful
    val context = LocalContext.current
    val resume = rememberResumeTick()
    var dismissed by remember { mutableStateOf(viewModel.widgetPromoDismissed) }
    var asked by remember { mutableStateOf(false) }
    val placed = remember(resume) { TaskWidget.placedIds(context).isNotEmpty() }

    AnimatedVisibility(
        visible = !dismissed && !placed,
        enter = fadeIn(tween(240)) + expandVertically(tween(240)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
    ) {
        MindfulCard(
            modifier = Modifier.fillMaxWidth(),
            background = c.lavender.copy(alpha = if (c.isDark) 0.18f else 0.55f),
            borderColor = Color.Transparent
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Widgets, contentDescription = null, tint = c.accentDeep)
                }
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text("Your list on the home screen", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Text(
                        "See what's urgent before you open anything. Tick tasks off and add new ones right there.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary
                    )
                }
            }
            if (asked) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Nothing popped up? Long-press an empty spot on your home screen, tap Widgets, find FocusGuard and drag it out.",
                    style = MaterialTheme.typography.bodySmall, color = c.textPrimary
                )
            }
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                PrimaryButton(
                    text = "Add widget",
                    onClick = {
                        asked = true
                        requestPinWidget(context)
                    },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Not now",
                    onClick = {
                        viewModel.widgetPromoDismissed = true
                        dismissed = true
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun requestPinWidget(context: Context) {
    try {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(context, TaskWidgetProvider::class.java), null, null)
        }
    } catch (_: Exception) {
        // The manual instructions are already on screen.
    }
}
