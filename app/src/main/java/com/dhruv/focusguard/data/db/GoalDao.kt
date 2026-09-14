package com.dhruv.focusguard.data.db

import androidx.room.*
import com.dhruv.focusguard.data.model.Goal
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Goal>>

    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<Goal>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): Goal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: Goal): Long

    @Delete
    suspend fun delete(goal: Goal)

    @Query("UPDATE goals SET progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int)
}
