package com.dhruv.focusguard

import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.ui.focus.FocusMode
import com.dhruv.focusguard.ui.focus.FocusPhase
import com.dhruv.focusguard.ui.focus.FocusSession
import com.dhruv.focusguard.ui.focus.clockText
import com.dhruv.focusguard.ui.sky.TaskKind
import com.dhruv.focusguard.ui.sky.kindOf
import com.dhruv.focusguard.ui.sky.tasksForDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class FocusLogicTest {

    @Test
    fun timerCountsPausesAndFinishes() {
        var s = FocusSession(FocusMode.Timer, null, 10 * 60_000L)
        assertEquals(FocusPhase.Idle, s.phase(0))
        s = s.start(1_000)
        assertEquals(FocusPhase.Running, s.phase(61_000))
        assertEquals(60_000, s.elapsed(61_000))
        assertEquals(9 * 60_000L, s.remaining(61_000))
        s = s.pause(61_000)
        assertEquals(FocusPhase.Paused, s.phase(500_000))
        assertEquals(60_000, s.elapsed(500_000))          // paused: no time passes
        s = s.start(500_000)
        assertEquals(FocusPhase.Finished, s.phase(500_000 + 9 * 60_000L))
        assertEquals(1f, s.progress(500_000 + 20 * 60_000L))
        s = s.reset()
        assertEquals(FocusPhase.Idle, s.phase(0))
    }

    @Test
    fun stopwatchKeepsGoingPastItsGoal() {
        val s = FocusSession(FocusMode.Stopwatch, null, 60_000L).start(0)
        assertEquals(FocusPhase.Running, s.phase(10 * 60_000L))
        assertEquals(1f, s.progress(10 * 60_000L))
        assertEquals("10:00", clockText(s.elapsed(10 * 60_000L)))
    }

    @Test
    fun targetIsClamped() {
        val s = FocusSession(FocusMode.Timer, null, 25 * 60_000L)
        assertEquals(FocusSession.MIN_TARGET, s.withTarget(5).targetMs)
        assertEquals(FocusSession.MAX_TARGET, s.withTarget(99 * 60 * 60_000L).targetMs)
    }

    @Test
    fun clockFormats() {
        assertEquals("1:30:00", clockText(90 * 60_000L))
        assertEquals("25:00", clockText(25 * 60_000L))
        assertEquals("0:20:00", clockText(20 * 60_000L, forceHours = true))
    }

    @Test
    fun kindsFromTitles() {
        fun k(t: String) = kindOf(Task(title = t))
        assertEquals(TaskKind.Study, k("Complete DSA practice set"))
        assertEquals(TaskKind.Health, k("Wake up before 7:30 AM"))
        assertEquals(TaskKind.Notes, k("Finish system modeling notes"))
        assertEquals(TaskKind.Water, k("Drink 2.5L water"))
        assertEquals(TaskKind.Reading, k("Read 10 pages of a book"))
        assertEquals(TaskKind.General, k("Buy a gift"))
        assertEquals(TaskKind.General, k("Get ready for the party"))   // "ready" is not "read"
    }

    @Test
    fun dayListIncludesOverdueAndUndatedOnlyToday() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 16, 9, 0) }.timeInMillis
        val day = 86_400_000L
        val active = listOf(
            Task(id = 1, title = "overdue", deadline = now - 2 * day),
            Task(id = 2, title = "undated"),
            Task(id = 3, title = "today", deadline = now + 3_600_000, priority = Priority.HIGH),
            Task(id = 4, title = "next week", deadline = now + 7 * day)
        )
        val done = listOf(Task(id = 5, title = "done today", isCompleted = true, completedAt = now - 60_000))
        assertEquals(listOf(1L, 3L, 2L, 5L), tasksForDay(active, done, now, now).map { it.id })
        assertEquals(listOf(4L), tasksForDay(active, done, now + 7 * day, now).map { it.id })
    }
}
