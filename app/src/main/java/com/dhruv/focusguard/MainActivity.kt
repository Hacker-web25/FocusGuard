package com.dhruv.focusguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.dhruv.focusguard.ui.navigation.AppNavGraph
import com.dhruv.focusguard.ui.theme.FocusGuardTheme
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(this)[TaskViewModel::class.java]

        setContent {
            FocusGuardTheme {
                AppNavGraph(viewModel = viewModel)
            }
        }
    }
}
