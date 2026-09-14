package com.dhruv.focusguard.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dhruv.focusguard.FocusGuardApp
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Task
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as FocusGuardApp).repository

    // ── Reactive streams ───────────────────────────────────────

    val activeTasks: StateFlow<List<Task>> = repo.getActiveTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completedTasks: StateFlow<List<Task>> = repo.getCompletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val goals: StateFlow<List<Goal>> = repo.getAllGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Task operations ────────────────────────────────────────

    fun saveTask(task: Task) {
        viewModelScope.launch { repo.saveTask(task) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { repo.deleteTask(task) }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch {
            if (task.isCompleted) repo.markIncomplete(task.id)
            else repo.markComplete(task.id)
        }
    }

    suspend fun getTaskById(id: Long): Task? = repo.getTaskById(id)

    // ── Goal operations ────────────────────────────────────────

    fun saveGoal(goal: Goal) {
        viewModelScope.launch { repo.saveGoal(goal) }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch { repo.deleteGoal(goal) }
    }

    fun updateGoalProgress(id: Long, progress: Int) {
        viewModelScope.launch { repo.updateGoalProgress(id, progress.coerceIn(0, 100)) }
    }

    // ── Settings ───────────────────────────────────────────────

    var timerDuration: Int
        get() = repo.timerDurationSec
        set(value) { repo.timerDurationSec = value }

    var cooldownMinutes: Int
        get() = repo.cooldownMinutes
        set(value) { repo.cooldownMinutes = value }

    var isInterceptionEnabled: Boolean
        get() = repo.isInterceptionEnabled
        set(value) { repo.isInterceptionEnabled = value }

    var monitoredApps: Set<String>
        get() = repo.monitoredApps
        set(value) { repo.monitoredApps = value }
}
