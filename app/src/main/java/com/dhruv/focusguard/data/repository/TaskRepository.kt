package com.dhruv.focusguard.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.dhruv.focusguard.data.db.AppDatabase
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Task
import kotlinx.coroutines.flow.Flow

class TaskRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val taskDao = db.taskDao()
    private val goalDao = db.goalDao()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("focusguard_prefs", Context.MODE_PRIVATE)

    // ── Tasks ──────────────────────────────────────────────────

    fun getActiveTasks(): Flow<List<Task>> = taskDao.getActiveTasks()
    fun getCompletedTasks(): Flow<List<Task>> = taskDao.getCompletedTasks()
    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks()
    fun getTasksForGoal(goalId: Long): Flow<List<Task>> = taskDao.getTasksForGoal(goalId)

    suspend fun getActiveTasksOnce(): List<Task> = taskDao.getActiveTasksOnce()
    suspend fun getTaskById(id: Long): Task? = taskDao.getById(id)
    suspend fun saveTask(task: Task): Long = taskDao.upsert(task)
    suspend fun deleteTask(task: Task) = taskDao.delete(task)
    /**
     * Completing a task that belongs to a goal waters that goal's plant:
     * +[GOAL_STEP_PER_TASK]% progress. Un-completing takes it back. Every
     * caller (app, pause screen, widget) comes through here, so it is
     * counted exactly once.
     */
    suspend fun markComplete(id: Long) {
        val task = taskDao.getById(id) ?: return
        if (task.isCompleted) return
        taskDao.markComplete(id)
        task.goalId?.let { goalDao.addProgress(it, GOAL_STEP_PER_TASK) }
    }

    suspend fun markIncomplete(id: Long) {
        val task = taskDao.getById(id) ?: return
        if (!task.isCompleted) return
        taskDao.markIncomplete(id)
        task.goalId?.let { goalDao.addProgress(it, -GOAL_STEP_PER_TASK) }
    }

    /** Add several tasks at once (quick add: one per line). */
    suspend fun addTasks(tasks: List<Task>) {
        for (t in tasks) taskDao.upsert(t)
    }

    // ── Goals ──────────────────────────────────────────────────

    fun getAllGoals(): Flow<List<Goal>> = goalDao.getAll()

    suspend fun getAllGoalsOnce(): List<Goal> = goalDao.getAllOnce()
    suspend fun getGoalById(id: Long): Goal? = goalDao.getById(id)
    suspend fun saveGoal(goal: Goal): Long = goalDao.upsert(goal)
    suspend fun deleteGoal(goal: Goal) = goalDao.delete(goal)
    suspend fun updateGoalProgress(id: Long, progress: Int) = goalDao.updateProgress(id, progress)

    // ── Settings ───────────────────────────────────────────────

    /** Comma-separated package names to intercept */
    var monitoredApps: Set<String>
        get() = prefs.getStringSet("monitored_apps", setOf("com.instagram.android")) ?: setOf("com.instagram.android")
        set(value) = prefs.edit().putStringSet("monitored_apps", value).apply()

    /** Base pause length in seconds. Penalties for skip attempts are added on top. */
    var timerDurationSec: Int
        get() = prefs.getInt("timer_duration", 60)
        set(value) = prefs.edit().putInt("timer_duration", value).apply()

    /** The user's first name, shown on the pause screen. */
    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(value) = prefs.edit().putString("user_name", value.trim()).apply()

    /** True once first-run onboarding (name + permissions) has been completed. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()

    /** The user closed the "add the widget" card on the Tasks screen. */
    var widgetPromoDismissed: Boolean
        get() = prefs.getBoolean("widget_promo_dismissed", false)
        set(value) = prefs.edit().putBoolean("widget_promo_dismissed", value).apply()

    /** Unsaved text from the quick-add box, so tapping outside never loses it. */
    var quickAddDraft: String
        get() = prefs.getString("quick_add_draft", "") ?: ""
        set(value) = prefs.edit().putString("quick_add_draft", value).apply()

    companion object {
        /** Goal progress gained per finished task linked to that goal. */
        const val GOAL_STEP_PER_TASK = 5
    }
}
