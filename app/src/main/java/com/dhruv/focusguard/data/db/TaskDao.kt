package com.dhruv.focusguard.data.db

import androidx.room.*
import com.dhruv.focusguard.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY priority DESC, deadline ASC")
    fun getActiveTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY priority DESC, deadline ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE goalId = :goalId AND isCompleted = 0")
    fun getTasksForGoal(goalId: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    // For overlay — synchronous read from background
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY priority DESC, deadline ASC")
    suspend fun getActiveTasksOnce(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: Task): Long

    @Delete
    suspend fun delete(task: Task)

    @Query("UPDATE tasks SET isCompleted = 1, completedAt = :now WHERE id = :id")
    suspend fun markComplete(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET isCompleted = 0, completedAt = NULL WHERE id = :id")
    suspend fun markIncomplete(id: Long)
}
