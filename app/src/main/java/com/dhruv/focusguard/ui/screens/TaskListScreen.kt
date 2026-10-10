package com.dhruv.focusguard.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.ui.components.BotanicalHeader
import com.dhruv.focusguard.ui.components.CompletionToggle
import com.dhruv.focusguard.ui.components.EmptyState
import com.dhruv.focusguard.ui.components.MindfulCard
import com.dhruv.focusguard.ui.components.MindfulProgress
import com.dhruv.focusguard.ui.components.PriorityBadge
import com.dhruv.focusguard.ui.components.PriorityDot
import com.dhruv.focusguard.ui.components.ScreenHeader
import com.dhruv.focusguard.ui.components.SectionLabel
import com.dhruv.focusguard.ui.components.priorityDotColor
import com.dhruv.focusguard.ui.theme.Radius
import com.dhruv.focusguard.ui.theme.Sizes
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tasks — the app's landing space.
 *
 * Opens with a gentle greeting and today's single most important task, then
 * the full list. All task data and every action come straight from the
 * view model; nothing here is hardcoded.
 */
@Composable
fun TaskListScreen(
    viewModel: TaskViewModel,
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit
) {
    val c = MaterialTheme.mindful
    val activeTasks by viewModel.activeTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    var showCompleted by remember { mutableStateOf(false) }

    val focusTask = activeTasks.firstOrNull()
    val completedToday = remember(completedTasks) { countCompletedToday(completedTasks) }
    val dayTotal = completedToday + activeTasks.size

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
                ScreenHeader(
                    title = greeting(),
                    subtitle = "Small steps create big changes."
                )
            }

            // ── Today's focus ──────────────────────────────────
            item(key = "focus") {
                Spacer(Modifier.height(Space.xxs))
                TodaysFocusCard(
                    task = focusTask,
                    completedToday = completedToday,
                    dayTotal = dayTotal,
                    onOpen = { focusTask?.let { onEditTask(it.id) } },
                    onToggle = { focusTask?.let { viewModel.toggleComplete(it) } }
                )
            }

            // ── List heading ───────────────────────────────────
            item(key = "listHeading") {
                Spacer(Modifier.height(Space.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Your tasks", modifier = Modifier.weight(1f))
                    if (activeTasks.isNotEmpty()) {
                        Text(
                            text = "${activeTasks.size} open",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                }
            }

            if (activeTasks.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Nothing on your list",
                        message = "Add what matters today, and it'll be waiting for you the next time you reach for a distraction.",
                        actionLabel = "Add your first task",
                        onAction = onAddTask
                    )
                }
            }

            items(activeTasks, key = { "task-${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { viewModel.toggleComplete(task) },
                    onClick = { onEditTask(task.id) }
                )
            }

            // ── Completed ──────────────────────────────────────
            if (completedTasks.isNotEmpty()) {
                item(key = "completedToggle") {
                    Spacer(Modifier.height(Space.xxs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Radius.field)
                            .clickable { showCompleted = !showCompleted }
                            .padding(vertical = Space.xs, horizontal = Space.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Completed",
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
                        TaskRow(
                            task = task,
                            onToggle = { viewModel.toggleComplete(task) },
                            onClick = { onEditTask(task.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddTask,
            containerColor = c.accentDeep,
            contentColor = com.dhruv.focusguard.ui.theme.PureWhite,
            shape = Radius.button,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Space.gutter, bottom = Space.lg)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add task")
        }
    }
}

/**
 * The one card that answers "what should I do next?".
 * Falls back to a calm all-clear state when nothing is pending.
 */
@Composable
private fun TodaysFocusCard(
    task: Task?,
    completedToday: Int,
    dayTotal: Int,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    val c = MaterialTheme.mindful

    MindfulCard(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.cardLarge,
        contentPadding = 0.dp
    ) {
        BotanicalHeader(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
        )

        Column(modifier = Modifier.padding(Space.md)) {
            SectionLabel("Today's focus")
            Spacer(Modifier.height(Space.xs))

            if (task != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompletionToggle(
                        completed = false,
                        accent = priorityDotColor(task.priority),
                        onToggle = onToggle,
                        contentDescription = "Mark ${task.title} complete",
                        modifier = Modifier.padding(end = Space.xxs)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(Radius.field)
                            .clickable(onClick = onOpen)
                            .padding(vertical = Space.xxs)
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        task.deadline?.let { due ->
                            Text(
                                text = deadlineLabel(due),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (due < System.currentTimeMillis()) c.danger else c.textSecondary
                            )
                        }
                    }
                    Spacer(Modifier.width(Space.xs))
                    PriorityBadge(task.priority)
                }
            } else {
                Text(
                    text = "You're all caught up",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary
                )
                Text(
                    text = "Nothing is waiting on you right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
            }

            if (dayTotal > 0) {
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MindfulProgress(
                        progress = completedToday.toFloat() / dayTotal,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = "$completedToday of $dayTotal done",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }
        }
    }
}

/** One task in the list. Compact, scannable, never a promotional card. */
@Composable
private fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val c = MaterialTheme.mindful
    val dot = priorityDotColor(task.priority)
    val overdue = !task.isCompleted && task.deadline != null &&
        task.deadline!! < System.currentTimeMillis()

    MindfulCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Space.xs
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompletionToggle(
                completed = task.isCompleted,
                accent = dot,
                onToggle = onToggle,
                contentDescription = if (task.isCompleted) {
                    "Mark ${task.title} not done"
                } else {
                    "Mark ${task.title} done"
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(Radius.field)
                    .clickable(onClick = onClick)
                    .padding(vertical = Space.xs, horizontal = Space.xxs)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (task.isCompleted) c.textSecondary else c.textPrimary,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                task.deadline?.let { due ->
                    Text(
                        text = deadlineLabel(due),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) c.danger else c.textSecondary
                    )
                }
            }

            Spacer(Modifier.width(Space.xxs))
            PriorityDot(task.priority)
            Spacer(Modifier.width(Space.sm))
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────

/** Time-of-day greeting. Derived from the clock, not stored anywhere. */
private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Still up?"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "Winding down"
    }
}

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
