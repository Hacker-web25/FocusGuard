package com.dhruv.focusguard.ui.sky

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.ui.graphics.vector.ImageVector
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * What a task is about, guessed from its words. Decides the icon and the
 * colour of its row, and a gentle line under the title when it has no notes.
 */
enum class TaskKind(val icon: ImageVector, val tone: Tone, val motto: String) {
    @Suppress("DEPRECATION")
    Study(Icons.Outlined.MenuBook, Tones.Red, "Consistency builds skills."),
    Health(Icons.Outlined.MonitorHeart, Tones.Teal, "Better mornings = better days."),
    Notes(Icons.Outlined.Description, Tones.Violet, "Quick revision, big impact."),
    Water(Icons.Outlined.WaterDrop, Tones.Blue, "Hydrate for a sharper you."),
    Reading(Icons.Outlined.AutoStories, Tones.Amber, "Small habits, big changes."),
    Fitness(Icons.Outlined.FitnessCenter, Tones.Teal, "Move a little, feel a lot."),
    Work(Icons.Outlined.WorkOutline, Tones.Blue, "Deep work wins the day."),
    General(Icons.Outlined.TaskAlt, Tones.Violet, "One step at a time.")
}

private val keywords: List<Pair<TaskKind, List<String>>> = listOf(
    TaskKind.Water to listOf("water", "hydrat", " drink"),
    TaskKind.Reading to listOf(" read", " book", " pages", " novel", " chapter"),
    TaskKind.Fitness to listOf(" gym", "workout", "exercise", " run ", " running", "pushup", "push-up", "cardio", " lift"),
    TaskKind.Health to listOf(" wake", " sleep", "meditat", " yoga", " walk", "health", "doctor", "breath", "morning", "skincare", "vitamin"),
    TaskKind.Notes to listOf(" note", "revis", "summar", " write", " essay", " report", " doc", "journal", " model"),
    TaskKind.Study to listOf(" dsa", "leetcode", "practice", " study", " exam", " test", " code", "coding", "problem", " solve", " course", " learn", "lecture", "assignment", "homework", " class", " quiz"),
    TaskKind.Work to listOf(" work", "meeting", " email", " mail", " client", " project", " deploy", " review", " call", " pitch", " invoice")
)

fun kindOf(task: Task): TaskKind {
    val text = " " + (task.title + " " + task.description).lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9.]+"), " ") + " "
    return keywords.firstOrNull { (_, words) -> words.any { text.contains(it) } }?.first ?: TaskKind.General
}

/** The line under the title: the task's own notes, else its kind's motto. */
fun subtitleOf(task: Task): String =
    task.description.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: kindOf(task).motto

val Task.isHighPriority: Boolean get() = priority == Priority.HIGH || priority == Priority.URGENT

/** "1h 30m", "30m", "2h". */
fun durationText(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

/** "16 Oct, 2026" — the mockups' date style. */
fun longDate(millis: Long): String = SimpleDateFormat("d MMM, yyyy", Locale.ENGLISH).format(Date(millis))

fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun sameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)

/** "Today", "Tomorrow", "Yesterday" or the date itself. */
fun dueLabel(deadline: Long, now: Long): String {
    val day = 24 * 60 * 60 * 1000L
    val d = startOfDay(deadline)
    val t = startOfDay(now)
    return when (d) {
        t -> "Today"
        t + day -> "Tomorrow"
        t - day -> "Yesterday"
        else -> longDate(deadline)
    }
}

/**
 * The tasks that belong on the list for [day]: on today, everything due today,
 * overdue or undated plus what was ticked off today; on other days, what's due
 * that day. Open tasks first (most pressing at the top), done ones after.
 */
fun tasksForDay(active: List<Task>, completed: List<Task>, day: Long, now: Long): List<Task> {
    val isToday = sameDay(day, now)
    val dayStart = startOfDay(day)
    val dayEnd = dayStart + 24 * 60 * 60 * 1000L
    val open = active.filter { t ->
        val dl = t.deadline
        if (isToday) dl == null || dl < dayEnd else dl != null && dl in dayStart until dayEnd
    }.sortedWith(
        compareBy<Task>(
            { if (it.deadline != null && it.deadline < startOfDay(now)) 0 else 1 },   // overdue first
            { -it.priority.weight },
            { it.deadline ?: Long.MAX_VALUE },
            { it.createdAt }
        )
    )
    val done = completed.filter { t ->
        val dl = t.deadline
        val at = t.completedAt ?: 0L
        if (isToday) (at in dayStart until dayEnd) || (dl != null && dl in dayStart until dayEnd)
        else dl != null && dl in dayStart until dayEnd
    }
    return open + done
}
