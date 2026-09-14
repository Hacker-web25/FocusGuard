package com.dhruv.focusguard.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.ui.theme.*
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskViewModel,
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit
) {
    val activeTasks by viewModel.activeTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    var showCompleted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FocusGuard", style = MaterialTheme.typography.headlineMedium) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (activeTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No tasks yet — tap + to add one",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            items(activeTasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onToggle = { viewModel.toggleComplete(task) },
                    onClick = { onEditTask(task.id) }
                )
            }

            if (completedTasks.isNotEmpty()) {
                item {
                    TextButton(onClick = { showCompleted = !showCompleted }) {
                        Text(
                            if (showCompleted) "Hide completed (${completedTasks.size})"
                            else "Show completed (${completedTasks.size})"
                        )
                    }
                }
            }

            if (showCompleted) {
                items(completedTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { viewModel.toggleComplete(task) },
                        onClick = { onEditTask(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: Task, onToggle: () -> Unit, onClick: () -> Unit) {
    val priorityColor = when (task.priority) {
        Priority.URGENT -> PriorityUrgent
        Priority.HIGH -> PriorityHigh
        Priority.MEDIUM -> PriorityMedium
        Priority.LOW -> PriorityLow
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Completion toggle
            IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                if (task.isCompleted) {
                    Icon(Icons.Filled.CheckCircle, "Complete", tint = PriorityLow)
                } else {
                    Icon(Icons.Outlined.Circle, "Incomplete", tint = priorityColor)
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                task.deadline?.let { dl ->
                    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                    val isOverdue = !task.isCompleted && dl < System.currentTimeMillis()
                    Text(
                        text = sdf.format(Date(dl)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverdue) PriorityUrgent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Priority badge
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(priorityColor)
            )
        }
    }
}
