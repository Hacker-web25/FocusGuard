package com.dhruv.focusguard.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.FocusGuardApp
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.data.repository.TaskRepository
import com.dhruv.focusguard.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fullscreen activity shown over the triggering app.
 * Displays active tasks + goals with a countdown timer.
 * User cannot dismiss until the timer completes.
 */
class OverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = (application as FocusGuardApp).repository
        val triggerApp = intent.getStringExtra(AppDetectorService.EXTRA_TRIGGER_APP) ?: "app"

        setContent {
            FocusGuardTheme(darkTheme = true) {
                OverlayScreen(
                    repo = repo,
                    triggerApp = triggerApp,
                    onDismiss = { finish() }
                )
            }
        }
    }

    @Deprecated("Block back button during timer")
    override fun onBackPressed() {
        // Intentionally blocked — OverlayScreen handles dismiss
    }
}

@Composable
private fun OverlayScreen(
    repo: TaskRepository,
    triggerApp: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val timerTotal = repo.timerDurationSec
    var secondsLeft by remember { mutableIntStateOf(timerTotal) }
    var canDismiss by remember { mutableStateOf(false) }

    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }

    // Load data
    LaunchedEffect(Unit) {
        tasks = repo.getActiveTasksOnce()
        goals = repo.getAllGoalsOnce()
    }

    // Countdown timer
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        canDismiss = true
    }

    val progress by animateFloatAsState(
        targetValue = secondsLeft.toFloat() / timerTotal,
        label = "timer"
    )

    val appLabel = triggerApp
        .removePrefix("com.")
        .split(".")
        .firstOrNull()
        ?.replaceFirstChar { it.uppercase() }
        ?: triggerApp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            // ── Header with timer ──────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Before you open $appLabel…",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(12.dp))

                // Big countdown
                Text(
                    text = formatTime(secondsLeft),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (secondsLeft <= 10) Highlight else Color.White
                )

                Spacer(Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Highlight,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Task list ──────────────────────────────────────
            Text(
                "YOUR TASKS",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.4f),
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tasks.isEmpty()) {
                    item {
                        Text(
                            "All clear — no pending tasks!",
                            color = PriorityLow,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                items(tasks) { task ->
                    OverlayTaskRow(task)
                }

                // Goals section
                if (goals.isNotEmpty()) {
                    item { Spacer(Modifier.height(12.dp)) }
                    item {
                        Text(
                            "YOUR GOALS",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.4f),
                            letterSpacing = 2.sp
                        )
                    }
                    items(goals) { goal ->
                        OverlayGoalRow(goal)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Dismiss button ─────────────────────────────────
            Button(
                onClick = onDismiss,
                enabled = canDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canDismiss) Highlight else Color.White.copy(alpha = 0.1f),
                    disabledContainerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (canDismiss) "Continue to $appLabel"
                    else "Review your tasks…",
                    color = if (canDismiss) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun OverlayTaskRow(task: Task) {
    val color = when (task.priority) {
        Priority.URGENT -> PriorityUrgent
        Priority.HIGH -> PriorityHigh
        Priority.MEDIUM -> PriorityMedium
        Priority.LOW -> PriorityLow
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            task.deadline?.let {
                val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                val isOverdue = it < System.currentTimeMillis()
                Text(
                    "Due ${sdf.format(Date(it))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOverdue) PriorityUrgent else Color.White.copy(alpha = 0.4f)
                )
            }
        }

        Text(
            task.priority.label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun OverlayGoalRow(goal: Goal) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                goal.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${goal.progress}%",
            style = MaterialTheme.typography.labelLarge,
            color = if (goal.progress >= 75) PriorityLow else Highlight
        )
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
