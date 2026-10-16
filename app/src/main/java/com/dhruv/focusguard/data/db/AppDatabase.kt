package com.dhruv.focusguard.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false
)
@TypeConverters(PriorityConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v2 adds a planned focus duration to tasks. Keeps every existing task. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN durationMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusguard.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
