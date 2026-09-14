package com.dhruv.focusguard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskScreen(
    viewModel: TaskViewModel,
    taskId: Long?,        // null = new task
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val goals by viewModel.goals.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var deadline by remember { mutableStateOf<Long?>(null) }
    var selectedGoalId by remember { mutableStateOf<Long?>(null) }
    var isLoaded by remember { mutableStateOf(taskId == null) }
    var existingTask by remember { mutableStateOf<Task?>(null) }

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

    // Date picker state
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = deadline ?: System.currentTimeMillis()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    deadline = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == null) "New Task" else "Edit Task") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (existingTask != null) {
                        IconButton(onClick = {
                            existingTask?.let { viewModel.deleteTask(it) }
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!isLoaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // Priority selector
            Text("Priority", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Priority.entries.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = priority == p,
                        onClick = { priority = p },
                        shape = SegmentedButtonDefaults.itemShape(index, Priority.entries.size)
                    ) {
                        Text(p.label)
                    }
                }
            }

            // Deadline
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    deadline?.let {
                        "Deadline: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it))}"
                    } ?: "Set deadline"
                )
            }
            if (deadline != null) {
                TextButton(onClick = { deadline = null }) {
                    Text("Clear deadline")
                }
            }

            // Link to goal
            if (goals.isNotEmpty()) {
                GoalDropdown(goals = goals, selectedId = selectedGoalId) { selectedGoalId = it }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (title.isBlank()) return@Button
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
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = title.isNotBlank()
            ) {
                Text(if (taskId == null) "Add Task" else "Save Changes")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDropdown(goals: List<Goal>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedGoal = goals.find { it.id == selectedId }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedGoal?.title ?: "No goal linked",
            onValueChange = {},
            readOnly = true,
            label = { Text("Link to goal") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = { onSelect(null); expanded = false }
            )
            goals.forEach { goal ->
                DropdownMenuItem(
                    text = { Text(goal.title) },
                    onClick = { onSelect(goal.id); expanded = false }
                )
            }
        }
    }
}
