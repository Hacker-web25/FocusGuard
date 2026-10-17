package com.dhruv.focusguard

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.overlay.RasterPolicy
import com.dhruv.focusguard.ui.focus.FocusMode
import com.dhruv.focusguard.ui.focus.FocusSession
import com.dhruv.focusguard.ui.screens.FocusContent
import com.dhruv.focusguard.ui.screens.TodoContent
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * Renders the sky screens to PNG on the JVM so they can be compared with
 * the design mockups. `mock` = the mockups' 2:3 frame (393x590dp at 417dpi
 * is exactly 1024px wide); `phone` = a 393x873dp phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ScreenshotTest {

    @Before
    fun setUp() {
        RasterPolicy.synchronous = true
        com.dhruv.focusguard.ui.sky.SkyMotion.enabled = false
    }

    private val now: Long = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 16, 8, 31, 0) }.timeInMillis

    private val tasks = listOf(
        Task(id = 1, title = "Complete DSA practice set", priority = Priority.HIGH, deadline = now + 3_600_000, durationMinutes = 90),
        Task(id = 2, title = "Wake up before 7:30 AM", priority = Priority.MEDIUM, deadline = now + 86_400_000L, durationMinutes = 30),
        Task(id = 3, title = "Finish system modeling notes", priority = Priority.MEDIUM, deadline = now + 3 * 86_400_000L, durationMinutes = 75),
        Task(id = 4, title = "Drink 2.5L water", priority = Priority.LOW, deadline = now - 86_400_000L, durationMinutes = 10),
        Task(id = 5, title = "Read 10 pages of a book", priority = Priority.LOW, durationMinutes = 20)
    )

    @Composable
    private fun Todo(reference: Boolean = true, list: List<Task> = tasks, guardDown: Boolean = false) {
        TodoContent(
            name = "Aryan", now = now, tasks = list, mood = 0.4f, celebrate = 0,
            protectionDown = guardDown, onFixProtection = {}, onToggle = {}, onOpen = {}, onEdit = {},
            onTimer = {}, onStopwatch = {}, onAdd = {}, onGoals = {}, onSettings = {}, reference = reference
        )
    }

    @Composable
    private fun Focus(session: FocusSession, reference: Boolean, clock: Long) {
        FocusContent(
            session = session, task = tasks[0], dayProgress = 0.4f, dateMillis = now, pickable = tasks,
            onBack = {}, onChange = {}, onOpenTask = {}, onPickTask = {}, onMarkDone = {},
            reference = reference, clock = { clock }
        )
    }

    private val running = FocusSession(FocusMode.Timer, 1L, 90 * 60_000L, bankedMs = 10 * 60_000L, runningSince = 1_000L)

    @Test
    @Config(qualifiers = "w393dp-h590dp-417dpi")
    fun todo_mock() = captureRoboImage("build/screens/todo_mock.png") { Todo() }

    @Test
    @Config(qualifiers = "w393dp-h873dp-440dpi")
    fun todo_phone() = captureRoboImage("build/screens/todo_phone.png") { Todo(reference = false) }

    @Test
    @Config(qualifiers = "w393dp-h590dp-417dpi")
    fun focus_mock() = captureRoboImage("build/screens/focus_mock.png") { Focus(running, true, 1_000L) }

    @Test
    @Config(qualifiers = "w393dp-h873dp-440dpi")
    fun focus_phone() = captureRoboImage("build/screens/focus_phone.png") { Focus(running.copy(bankedMs = 70 * 60_000L), false, 1_000L) }

    @Test
    @Config(qualifiers = "w393dp-h873dp-440dpi")
    fun focus_idle_phone() = captureRoboImage("build/screens/focus_idle_phone.png") {
        Focus(FocusSession(FocusMode.Timer, 1L, 90 * 60_000L), false, 1_000L)
    }

    @Test
    @Config(qualifiers = "w393dp-h873dp-440dpi")
    fun stopwatch_paused_phone() = captureRoboImage("build/screens/stopwatch_paused_phone.png") {
        Focus(FocusSession(FocusMode.Stopwatch, 1L, 25 * 60_000L, bankedMs = 12 * 60_000L + 34_000L), false, 1_000L)
    }

    @Test
    @Config(qualifiers = "w393dp-h873dp-440dpi")
    fun focus_finished_phone() = captureRoboImage("build/screens/focus_finished_phone.png") {
        Focus(FocusSession(FocusMode.Timer, 1L, 90 * 60_000L, bankedMs = 90 * 60_000L, finished = true), false, 1_000L)
    }

    @Test
    @Config(qualifiers = "w393dp-h873dp-440dpi")
    fun todo_done_phone() = captureRoboImage("build/screens/todo_done_phone.png") {
        val done = tasks.mapIndexed { i, t -> if (i == 1 || i == 3) t.copy(isCompleted = true, completedAt = now) else t }
        Todo(reference = false, list = done.sortedBy { it.isCompleted }, guardDown = true)
    }

    @Test
    @Config(qualifiers = "w393dp-h873dp-440dpi")
    fun todo_empty_phone() = captureRoboImage("build/screens/todo_empty_phone.png") {
        Todo(reference = false, list = emptyList())
    }

    @Test
    @Config(qualifiers = "w393dp-h873dp-440dpi")
    fun todo_many_phone() = captureRoboImage("build/screens/todo_many_phone.png") {
        val more = tasks + List(5) { i ->
            Task(id = 10L + i, title = "Extra task ${i + 1}", deadline = now + (i + 3) * 86_400_000L, durationMinutes = 15)
        }
        Todo(reference = false, list = more)
    }
}
