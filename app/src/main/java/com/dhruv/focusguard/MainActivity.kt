package com.dhruv.focusguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.dhruv.focusguard.guard.Protection
import com.dhruv.focusguard.guard.Watchdog
import com.dhruv.focusguard.ui.navigation.AppNavGraph
import com.dhruv.focusguard.ui.theme.FocusGuardTheme
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        val openAddTask = intent?.getBooleanExtra(EXTRA_OPEN_ADD_TASK, false) == true

        setContent {
            FocusGuardTheme {
                AppNavGraph(viewModel = viewModel, openAddTask = openAddTask)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app after FocusGuard was killed repairs the guard (if auto-repair
        // was granted over adb) and restarts the once-a-minute watchdog.
        Protection.healIfNeeded(this)
        Watchdog.schedule(this)
    }

    companion object {
        /** Set by the pause screen's "Add a task" button. */
        const val EXTRA_OPEN_ADD_TASK = "open_add_task"
    }
}
