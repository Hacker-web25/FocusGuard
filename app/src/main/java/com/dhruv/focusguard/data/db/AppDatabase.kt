package com.dhruv.focusguard.data.db

import android.content.Context
import androidx.room.*
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task

class PriorityConverter {
    @TypeConverter
    fun fromPriority(priority: Priority): Int = priority.ordinal

    @TypeConverter
    fun toPriority(ordinal: Int): Priority = Priority.fromOrdinal(ordinal)
}

@Database(
    entities = [Task::class, Goal::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(PriorityConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusguard.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
