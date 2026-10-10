package com.dhruv.focusguard.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.FocusGuardApp
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.data.repository.TaskRepository
import com.dhruv.focusguard.ui.components.BotanicalOrb
import com.dhruv.focusguard.ui.components.MindfulCard
import com.dhruv.focusguard.ui.components.MindfulProgress
import com.dhruv.focusguard.ui.components.NightSky
import com.dhruv.focusguard.ui.components.PriorityBadge
import com.dhruv.focusguard.ui.components.PriorityDot
import com.dhruv.focusguard.ui.components.SectionLabel
import com.dhruv.focusguard.ui.components.rememberReducedMotion
import com.dhruv.focusguard.ui.theme.FocusNightTheme
import com.dhruv.focusguard.ui.theme.Radius
import com.dhruv.focusguard.ui.theme.Sizes
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fullscreen activity shown over the triggering app.
 *
 * Behaviour is unchanged from the original: the countdown runs for the
 * configured duration, the back button stays blocked, and the activity only
 * finishes when the user taps Continue after the timer completes.
 */
class OverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = (application as FocusGuardApp).repository
        val triggerApp = intent.getStringExtra(AppDetectorService.EXTRA_TRIGGER_APP) ?: "app"

        setContent {
            FocusNightTheme {
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
    val c = MaterialTheme.mindful
    val reducedMotion = rememberReducedMotion()

    val timerTotal = remember { repo.timerDurationSec }
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

    val appLabel = remember(triggerApp) { prettyAppName(triggerApp) }
    val fraction = if (timerTotal > 0) secondsLeft.toFloat() / timerTotal else 0f

    val focusTask = tasks.firstOrNull()
    val restOfTasks = if (tasks.size > 1) tasks.drop(1) else emptyList()
    val nextGoal = goals.minByOrNull { it.progress }

    Box(modifier = Modifier.fillMaxSize()) {
        NightSky(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Scrollable body ────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter)
            ) {
                Spacer(Modifier.height(Space.lg))

                Text(
                    text = "Take a mindful pause",
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.focusText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = "You're about to open $appLabel. Let's make this moment count.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.focusTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Space.md))

                TimerOrb(
                    fraction = fraction,
                    timeText = formatTime(secondsLeft),
                    breathing = !reducedMotion && secondsLeft > 0,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 260.dp, height = 290.dp)
                )

                Spacer(Modifier.height(Space.lg))

                // ── Today's focus ──────────────────────────────
                SectionLabel("Today's focus", color = c.focusTextMuted)
                Spacer(Modifier.height(Space.xs))

                if (focusTask != null) {
                    MindfulCard(
                        modifier = Modifier.fillMaxWidth(),
                        background = c.focusSurface,
                        borderColor = c.focusBorder,
                        elevated = false
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PriorityDot(focusTask.priority)
                            Spacer(Modifier.width(Space.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = focusTask.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = c.focusText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val due = focusTask.deadline?.let { dueLabel(it) }
                                if (due != null) {
                                    Text(
                                        text = due,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (focusTask.deadline!! < System.currentTimeMillis()) {
                                            c.dotUrgent
                                        } else {
                                            c.focusTextMuted
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.width(Space.xs))
                            PriorityBadge(focusTask.priority)
                        }
                    }
                } else {
                    MindfulCard(
                        modifier = Modifier.fillMaxWidth(),
                        background = c.focusSurface,
                        borderColor = c.focusBorder,
                        elevated = false
                    ) {
                        Text(
                            text = "All clear — nothing pending",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.accent
                        )
                        Text(
                            text = "Enjoy the scroll. You've earned it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.focusTextMuted
                        )
                    }
                }

                // ── Remaining tasks ────────────────────────────
                if (restOfTasks.isNotEmpty()) {
                    Spacer(Modifier.height(Space.md))
                    SectionLabel("Also on your list", color = c.focusTextMuted)
                    Spacer(Modifier.height(Space.xs))
                    restOfTasks.forEach { task ->
                        NightTaskRow(task)
                        Spacer(Modifier.height(Space.xs))
                    }
                }

                // ── Next goal ──────────────────────────────────
                if (nextGoal != null) {
                    Spacer(Modifier.height(Space.md))
                    SectionLabel("Next goal", color = c.focusTextMuted)
                    Spacer(Modifier.height(Space.xs))
                    MindfulCard(
                        modifier = Modifier.fillMaxWidth(),
                        background = c.focusSurface,
                        borderColor = c.focusBorder,
                        elevated = false
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = nextGoal.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = c.focusText,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(Space.sm))
                            Text(
                                text = "${nextGoal.progress}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = c.accent
                            )
                        }
                        Spacer(Modifier.height(Space.xs))
                        MindfulProgress(
                            progress = nextGoal.progress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = c.accent,
                            trackColor = Color.White.copy(alpha = 0.10f),
                            animate = !reducedMotion
                        )
                    }
                }

                Spacer(Modifier.height(Space.lg))
            }

            // ── Pinned action ──────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .padding(bottom = Space.lg, top = Space.xs)
            ) {
                Button(
                    onClick = onDismiss,
                    enabled = canDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizes.buttonHeight),
                    shape = Radius.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.focusText,
                        contentColor = c.focusCanvas,
                        disabledContainerColor = Color.White.copy(alpha = 0.08f),
                        disabledContentColor = c.focusTextMuted
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        text = if (canDismiss) "Continue to $appLabel" else "Take a breath…",
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (canDismiss) {
                        Spacer(Modifier.width(Space.xs))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSm)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The glowing orb with the countdown ring beneath it.
 * [fraction] is remaining time as a 0..1 value.
 */
@Composable
private fun TimerOrb(
    fraction: Float,
    timeText: String,
    breathing: Boolean,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.mindful
    val safeFraction = fraction.coerceIn(0f, 1f)

    Box(modifier = modifier) {
        BotanicalOrb(
            breathing = breathing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(205.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(142.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(142.dp)
                    .clip(Radius.chip)
                    .background(c.focusCanvasDeep.copy(alpha = 0.62f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), Radius.chip)
            )

            Canvas(modifier = Modifier.size(142.dp)) {
                val strokeW = 5.dp.toPx()
                val inset = strokeW / 2f + 6.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

                drawArc(
                    color = Color.White.copy(alpha = 0.12f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                if (safeFraction > 0f) {
                    drawArc(
                        color = c.accent,
                        startAngle = -90f,
                        sweepAngle = 360f * safeFraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = timeText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.focusText
                )
                Text(
                    text = "Time left",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.focusTextMuted
                )
            }
        }
    }
}

@Composable
private fun NightTaskRow(task: Task) {
    val c = MaterialTheme.mindful
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.field)
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = Space.sm, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PriorityDot(task.priority, size = 8.dp)
        Spacer(Modifier.width(Space.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = c.focusText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            task.deadline?.let {
                Text(
                    text = dueLabel(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it < System.currentTimeMillis()) c.dotUrgent else c.focusTextMuted
                )
            }
        }
        Spacer(Modifier.width(Space.xs))
        Text(
            text = task.priority.label,
            style = MaterialTheme.typography.labelMedium,
            color = c.focusTextMuted
        )
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun dueLabel(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return "Due ${sdf.format(Date(millis))}"
}

/** Friendly name for the app that triggered the pause. */
private fun prettyAppName(pkg: String): String = when (pkg) {
    "com.instagram.android" -> "Instagram"
    "com.google.android.youtube" -> "YouTube"
    "com.facebook.katana" -> "Facebook"
    "com.twitter.android" -> "X"
    "com.snapchat.android" -> "Snapchat"
    "com.reddit.frontpage" -> "Reddit"
    "com.linkedin.android" -> "LinkedIn"
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "TikTok"
    "com.whatsapp" -> "WhatsApp"
    else -> pkg.removePrefix("com.")
        .substringBefore('.')
        .replaceFirstChar { it.uppercase() }
}
