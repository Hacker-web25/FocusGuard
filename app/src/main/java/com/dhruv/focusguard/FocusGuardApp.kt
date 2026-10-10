package com.dhruv.focusguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dhruv.focusguard.data.repository.TaskRepository
import com.dhruv.focusguard.widget.TaskWidget

class FocusGuardApp : Application() {

    lateinit var repository: TaskRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = TaskRepository(this)
        createNotificationChannel()
        // Keep the home-screen widget in step with every task change.
        TaskWidget.observe(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FocusGuard Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps FocusGuard monitoring running"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "focusguard_service"
    }
}
