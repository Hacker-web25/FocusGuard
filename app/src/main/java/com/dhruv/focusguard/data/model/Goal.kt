package com.dhruv.focusguard.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val progress: Int = 0,               // 0–100
    val targetDate: Long? = null,        // epoch millis
    val createdAt: Long = System.currentTimeMillis()
)
