package com.dhruv.focusguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.ui.components.MindfulField
import com.dhruv.focusguard.ui.components.PrimaryButton
import com.dhruv.focusguard.ui.components.SectionLabel
import com.dhruv.focusguard.ui.components.SelectableChip
import com.dhruv.focusguard.ui.components.SelectorRow
import com.dhruv.focusguard.ui.components.priorityChipColors
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
 * Create or edit a task.
 *
 * The form fields map one-to-one onto the existing [Task] model — title,
 * description, priority, deadline and the optional linked goal. No schema
 * changes; the save path still goes through the view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskScreen(
    viewModel: TaskViewModel,
    taskId: Long?,        // null = new task
    onBack: () -> Unit
) {
    val c = MaterialTheme.mindful
    val goals by viewModel.goals.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var deadline by remember { mutableStateOf<Long?>(null) }
    var selectedGoalId by remember { mutableStateOf<Long?>(null) }
    var isLoaded by remember { mutableStateOf(taskId == null) }
    var existingTask by remember { mutableStateOf<Task?>(null) }
    var titleTouched by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Load existing task
    LaunchedEffect(taskId) {
        if (taskId != null) {
            viewModel.getTaskById(taskId)?.let { t ->
                existingTask = t
                title = t.title
                description = t.description
                priority = t.priority
                deadline = t.deadline
                selectedGoalId = t.goalId
            }
            isLoaded = true
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = deadline ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    deadline = datePickerState.selectedDateMillis?.let { endOfDay(it) }
                    showDatePicker = false
                }) { Text("Set deadline") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val titleInvalid = titleTouched && title.isBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    text = if (taskId == null) "New task" else "Edit task",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (existingTask != null) {
                    HeaderIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = "Delete task",
                        onClick = {
                            existingTask?.let { viewModel.deleteTask(it) }
                            onBack()
                        },
                        background = c.chipUrgent,
                        tint = c.chipUrgentInk
                    )
                }
            }

            if (!isLoaded) {
                // Brief load state while the existing task is fetched
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Opening…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                }
                return@Column
            }

            // ── Form ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                MindfulField(
                    label = "Task name",
                    value = title,
                    onValueChange = {
                        title = it
                        titleTouched = true
                    },
                    placeholder = "e.g. Finish the migration doc",
                    isError = titleInvalid,
                    supportingText = if (titleInvalid) "Give your task a name to save it" else null
                )

                // Deadline
                Column {
                    SectionLabel("Deadline")
                    Spacer(Modifier.height(Space.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        SelectableChip(
                            text = "Today",
                            selected = deadline != null && isSameDay(deadline!!, daysFromNow(0)),
                            onClick = { deadline = daysFromNow(0) },
                            modifier = Modifier.weight(1f)
                        )
                        SelectableChip(
                            text = "Tomorrow",
                            selected = deadline != null && isSameDay(deadline!!, daysFromNow(1)),
                            onClick = { deadline = daysFromNow(1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(Space.xs))
                    SelectorRow(
                        text = deadline?.let {
                            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(it))
                        } ?: "Pick a date",
                        placeholderStyle = deadline == null,
                        onClick = { showDatePicker = true },
                        leadingIcon = Icons.Filled.DateRange,
                        trailingIcon = Icons.Filled.KeyboardArrowDown
                    )
                    if (deadline != null) {
                        Spacer(Modifier.height(Space.xxs))
                        Row(
                            modifier = Modifier
                                .clip(Radius.chip)
                                .clickable(role = Role.Button) { deadline = null }
                                .padding(horizontal = Space.xs, vertical = Space.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                tint = c.textSecondary,
                                modifier = Modifier.size(Sizes.iconSm)
                            )
                            Spacer(Modifier.width(Space.xxs))
                            Text(
                                text = "Clear deadline",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                    }
                }

                // Priority
                Column {
                    SectionLabel("Priority")
                    Spacer(Modifier.height(Space.xs))
                    // Two rows of two so every label stays readable on narrow phones
                    Priority.entries.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.xs)
                        ) {
                            pair.forEach { p ->
                                val (container, ink) = priorityChipColors(p)
                                SelectableChip(
                                    text = p.label,
                                    selected = priority == p,
                                    onClick = { priority = p },
                                    selectedContainer = container,
                                    selectedContent = ink,
                                    leadingDot = priorityDotColor(p),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.xs))
                    }
                }

                // Notes
                MindfulField(
                    label = "Notes (optional)",
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "Anything that helps you start",
                    singleLine = false,
                    minLines = 3
                )

                // Linked goal
                if (goals.isNotEmpty()) {
                    GoalSelector(
                        goals = goals,
                        selectedId = selectedGoalId,
                        onSelect = { selectedGoalId = it }
                    )
                }

                Spacer(Modifier.height(Space.xxs))
            }

            // ── Save ───────────────────────────────────────────
            Column(
                // Bottom inset is already applied by the Scaffold in AppNavGraph
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .padding(top = Space.sm, bottom = Space.md)
            ) {
                PrimaryButton(
                    text = if (taskId == null) "Save task" else "Save changes",
                    onClick = {
                        titleTouched = true
                        val task = Task(
                            id = existingTask?.id ?: 0,
                            title = title.trim(),
                            description = description.trim(),
                            priority = priority,
                            deadline = deadline,
                            goalId = selectedGoalId,
                            isCompleted = existingTask?.isCompleted ?: false,
                            completedAt = existingTask?.completedAt,
                            createdAt = existingTask?.createdAt ?: System.currentTimeMillis()
                        )
                        viewModel.saveTask(task)
                        onBack()
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Soft circular header action. Carries its own accessibility label. */
@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color? = null,
    tint: Color? = null
) {
    val c = MaterialTheme.mindful
    Box(
        modifier = modifier
            .size(Sizes.touchTarget)
            .clip(Radius.chip)
            .background(background ?: c.surface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: c.textPrimary,
            modifier = Modifier.size(Sizes.icon)
        )
    }
}

/** Goal link selector, built on a stable DropdownMenu. */
@Composable
private fun GoalSelector(
    goals: List<Goal>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    val c = MaterialTheme.mindful
    var expanded by remember { mutableStateOf(false) }
    val selectedGoal = goals.find { it.id == selectedId }

    Column {
        SectionLabel("Long-term goal")
        Spacer(Modifier.height(Space.xs))
        Box {
            SelectorRow(
                text = selectedGoal?.title ?: "Not linked to a goal",
                placeholderStyle = selectedGoal == null,
                onClick = { expanded = true },
                trailingIcon = Icons.Filled.KeyboardArrowDown
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Not linked to a goal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary
                        )
                    },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    }
                )
                goals.forEach { goal ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                goal.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textPrimary
                            )
                        },
                        onClick = {
                            onSelect(goal.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ── Date helpers ───────────────────────────────────────────────

/**
 * Deadlines are stored at the end of the chosen local day, so a task due
 * "Today" does not read as overdue the moment it is created.
 */
private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun daysFromNow(days: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, days)
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
