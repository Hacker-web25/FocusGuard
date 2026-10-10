package com.dhruv.focusguard.guard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.overlay.OverlayLifecycleOwner
import com.dhruv.focusguard.guard.overlay.PauseActions
import com.dhruv.focusguard.guard.overlay.PauseScreen
import com.dhruv.focusguard.guard.overlay.PauseState

/**
 * Hosts the Compose [PauseScreen] in an accessibility-overlay window owned by
 * the accessibility service.
 *
 * Not a task, so it never appears in recents and can't be swiped away. Starts
 * without the "display over other apps" or MIUI pop-up permissions. The window
 * paints solid night colour the instant it is added, before Compose has drawn
 * its first frame, so Instagram is covered as early as possible.
 */
class LockOverlay(
    private val context: Context,
    private val haptics: Haptics,
    private val actions: PauseActions
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private var state: PauseState? = null

    val isShowing: Boolean get() = root != null

    fun show(
        totalSeconds: Int,
        penaltySeconds: Int,
        strikes: Int,
        justPenalized: Boolean,
        userName: String,
        opensToday: Int,
        walkAwaysToday: Int
    ) {
        hide()

        val st = PauseState().apply {
            this.userName = userName
            this.totalMs = totalSeconds * 1000L
            this.deadlineElapsed = SystemClock.elapsedRealtime() + totalSeconds * 1000L
            this.penaltySeconds = penaltySeconds
            this.strikes = strikes
            this.justPenalized = justPenalized
            this.opensToday = opensToday
            this.walkAwaysToday = walkAwaysToday
        }

        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val frame = BackSwallowingFrame(context).apply {
            setBackgroundColor(NIGHT)
            isFocusable = true
            isFocusableInTouchMode = true
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }
        val compose = ComposeView(context).apply {
            setContent { PauseScreen(state = st, actions = actions, haptics = haptics) }
        }
        frame.addView(
            compose,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
        }

        try {
            wm.addView(frame, params)
            root = frame
            owner = lifecycleOwner
            state = st
            frame.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Could not add lock overlay", e)
            lifecycleOwner.destroy()
        }
    }

    fun hide() {
        val v = root ?: return
        root = null
        state = null
        try {
            wm.removeViewImmediate(v)
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed", e)
        }
        owner?.destroy()
        owner = null
    }

    fun setContent(tasks: List<Task>, goal: Goal?) {
        val st = state ?: return
        st.tasks = tasks
        st.goal = goal
        st.tasksLoaded = true
    }

    /** Root view that eats Back so it never reaches Instagram underneath. */
    @SuppressLint("ViewConstructor")
    private class BackSwallowingFrame(context: Context) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
            return super.dispatchKeyEvent(event)
        }
    }

    companion object {
        private const val TAG = "LockOverlay"
        private val NIGHT = Color.parseColor("#070B18")
    }
}
