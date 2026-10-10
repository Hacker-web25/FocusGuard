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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.ui.components.EmptyState
import com.dhruv.focusguard.ui.components.IconTile
import com.dhruv.focusguard.ui.components.MindfulCard
import com.dhruv.focusguard.ui.components.MindfulField
import com.dhruv.focusguard.ui.components.MindfulProgress
import com.dhruv.focusguard.ui.components.ScreenHeader
import com.dhruv.focusguard.ui.theme.PureWhite
import com.dhruv.focusguard.ui.theme.Radius
import com.dhruv.focusguard.ui.theme.Sizes
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel

/**
 * Long-term goals.
 *
 * Goal creation, deletion and progress updates all run through the existing
 * view model calls; the accent colour per card is derived from the goal's
 * own id so a list stays varied but stable between launches.
 */
@Composable
fun GoalsScreen(viewModel: TaskViewModel) {
    val c = MaterialTheme.mindful
    val goals by viewModel.goals.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, desc ->
                viewModel.saveGoal(Goal(title = title, description = desc))
                showAddDialog = false
            }
        )
    }

    val accents = listOf(c.accentSoft, c.lavender, c.powderBlue, c.peach, c.blush)
    val inks = listOf(c.accentDeep, c.textPrimary, c.textPrimary, c.textPrimary, c.textPrimary)

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
            item(key = "header") {
                ScreenHeader(
                    title = "Your goals",
                    subtitle = if (goals.isEmpty()) {
                        "What are you working toward?"
                    } else {
                        "${goals.size} in progress"
                    }
                )
                Spacer(Modifier.height(Space.xxs))
            }

            if (goals.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No goals yet",
                        message = "Name something that matters over the next few months. You'll see it every time you pause before a distraction.",
                        actionLabel = "Add a goal",
                        onAction = { showAddDialog = true }
                    )
                }
            }

            items(goals, key = { it.id }) { goal ->
                val index = (goal.id.toInt().mod(accents.size))
                GoalCard(
                    goal = goal,
                    accent = accents[index],
                    accentInk = inks[index],
                    onProgressChange = { viewModel.updateGoalProgress(goal.id, it) },
                    onDelete = { viewModel.deleteGoal(goal) }
                )
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = c.accentDeep,
            contentColor = PureWhite,
            shape = Radius.button,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Space.gutter, bottom = Space.lg)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add goal")
        }
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    accent: Color,
    accentInk: Color,
    onProgressChange: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val c = MaterialTheme.mindful
    val complete = goal.progress >= 100

    MindfulCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(
                icon = Icons.Filled.Star,
                tint = accentInk,
                background = accent,
                contentDescription = null
            )
            Spacer(Modifier.width(Space.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (goal.description.isNotBlank()) {
                    Text(
                        text = goal.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(Space.xs))
            Box(
                modifier = Modifier
                    .size(Sizes.touchTarget)
                    .clip(Radius.chip)
                    .clickable(role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete goal ${goal.title}",
                    tint = c.textSecondary,
                    modifier = Modifier.size(Sizes.icon)
                )
            }
        }

        Spacer(Modifier.height(Space.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            MindfulProgress(
                progress = goal.progress / 100f,
                modifier = Modifier.weight(1f),
                color = if (complete) c.accentDeep else c.accent
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                text = "${goal.progress}%",
                style = MaterialTheme.typography.labelLarge,
                color = if (complete) c.accentDeep else c.textPrimary
            )
        }

        Spacer(Modifier.height(Space.xs))

        if (complete) {
            Text(
                text = "Complete — nicely done.",
                style = MaterialTheme.typography.bodySmall,
                color = c.accentDeep
            )
            Spacer(Modifier.height(Space.xxs))
        }

        // Progress nudges — each writes through the existing view model call
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            listOf(-10, -5, 5, 10).forEach { delta ->
                ProgressNudge(
                    delta = delta,
                    enabled = if (delta < 0) goal.progress > 0 else goal.progress < 100,
                    onClick = { onProgressChange((goal.progress + delta).coerceIn(0, 100)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProgressNudge(
    delta: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.mindful
    val label = if (delta > 0) "+$delta%" else "$delta%"
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(Radius.chip)
            .background(if (enabled) c.surfaceSunken else Color.Transparent)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) c.textPrimary else c.disabledText
        )
    }
}

@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val c = MaterialTheme.mindful
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = Radius.card,
        title = {
            Text(
                text = "New goal",
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                MindfulField(
                    label = "Goal",
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "e.g. Learn system design"
                )
                MindfulField(
                    label = "Why it matters (optional)",
                    value = desc,
                    onValueChange = { desc = it },
                    placeholder = "A line to remind yourself",
                    singleLine = false,
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), desc.trim()) },
                enabled = title.isNotBlank()
            ) {
                Text("Save goal", color = if (title.isNotBlank()) c.accentDeep else c.disabledText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = c.textSecondary)
            }
        }
    )
}
