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
    suspend fun markComplete(id: Long) = taskDao.markComplete(id)
    suspend fun markIncomplete(id: Long) = taskDao.markIncomplete(id)

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

    /** Overlay timer duration in seconds */
    var timerDurationSec: Int
        get() = prefs.getInt("timer_duration", 60)
        set(value) = prefs.edit().putInt("timer_duration", value).apply()

    /** Cooldown in minutes — don't re-trigger within this window */
    var cooldownMinutes: Int
        get() = prefs.getInt("cooldown_minutes", 5)
        set(value) = prefs.edit().putInt("cooldown_minutes", value).apply()

    /** Whether the interception is enabled */
    var isInterceptionEnabled: Boolean
        get() = prefs.getBoolean("interception_enabled", true)
        set(value) = prefs.edit().putBoolean("interception_enabled", value).apply()

    /** Timestamp of last overlay shown */
    var lastOverlayShownAt: Long
        get() = prefs.getLong("last_overlay_shown", 0L)
        set(value) = prefs.edit().putLong("last_overlay_shown", value).apply()

    fun isCooldownActive(): Boolean {
        val elapsed = System.currentTimeMillis() - lastOverlayShownAt
        return elapsed < cooldownMinutes * 60_000L
    }
}
