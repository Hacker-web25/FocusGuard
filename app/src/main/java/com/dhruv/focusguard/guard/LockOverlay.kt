package com.dhruv.focusguard.guard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dhruv.focusguard.data.model.Goal
import com.dhruv.focusguard.data.model.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Full-screen pause drawn as an accessibility-overlay window owned by the
 * accessibility service.
 *
 * Unlike the old OverlayActivity this is not a task: it never appears in
 * recents, cannot be swiped away, starts instantly, and needs neither the
 * "display over other apps" permission nor MIUI's "pop-up from background"
 * permission. It sits above Instagram and swallows all touches and Back.
 *
 * Built with plain Views on purpose: Compose needs a lifecycle owner that a
 * service window does not have. The visual redesign can come later.
 */
class LockOverlay(
    private val context: Context,
    private val onCountdownFinished: () -> Unit,
    private val onContinue: () -> Unit,
    private val onCloseTarget: () -> Unit
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var root: View? = null
    private var deadlineElapsed = 0L
    private var totalMs = 1L
    private var finished = false

    private lateinit var timeText: TextView
    private lateinit var ring: RingView
    private lateinit var statusText: TextView
    private lateinit var continueButton: TextView
    private lateinit var tasksContainer: LinearLayout
    private lateinit var goalText: TextView

    val isShowing: Boolean get() = root != null

    private val ticker = object : Runnable {
        override fun run() {
            if (root == null) return
            val remaining = deadlineElapsed - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                render(0)
                if (!finished) {
                    finished = true
                    showFinishedState()
                    onCountdownFinished()
                }
                return
            }
            render(remaining)
            handler.postDelayed(this, 200)
        }
    }

    fun show(
        totalSeconds: Int,
        penaltySeconds: Int,
        strikes: Int,
        justPenalized: Boolean,
        userName: String
    ) {
        hide()
        finished = false
        totalMs = totalSeconds * 1000L
        deadlineElapsed = SystemClock.elapsedRealtime() + totalMs

        val view = buildView(userName, penaltySeconds, strikes, justPenalized)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
        }
        try {
            wm.addView(view, params)
            root = view
            view.requestFocus()
            handler.post(ticker)
        } catch (e: Exception) {
            Log.e(TAG, "Could not add lock overlay", e)
            root = null
        }
    }

    fun hide() {
        handler.removeCallbacks(ticker)
        val v = root ?: return
        root = null
        try {
            wm.removeViewImmediate(v)
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed", e)
        }
    }

    fun setContent(tasks: List<Task>, goal: Goal?) {
        if (root == null) return
        tasksContainer.removeAllViews()
        if (tasks.isEmpty()) {
            tasksContainer.addView(bodyText("No open tasks. What would you rather be doing?", MUTED))
        } else {
            tasks.take(4).forEach { task ->
                val due = task.deadline?.let { "  ·  ${dueLabel(it)}" } ?: ""
                tasksContainer.addView(
                    bodyText("•  ${task.title}$due", TEXT).apply {
                        setPadding(0, dp(6), 0, dp(6))
                    }
                )
            }
            if (tasks.size > 4) {
                tasksContainer.addView(bodyText("+ ${tasks.size - 4} more", MUTED))
            }
        }
        if (goal != null) {
            goalText.text = "${goal.title}  ·  ${goal.progress}%"
            goalText.visibility = View.VISIBLE
            (goalText.tag as? View)?.visibility = View.VISIBLE
        }
    }

    // ── Rendering ───────────────────────────────────────────────

    private fun render(remainingMs: Long) {
        val secs = ((remainingMs + 999) / 1000).toInt()
        timeText.text = String.format(Locale.US, "%d:%02d", secs / 60, secs % 60)
        ring.progress = 1f - (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    }

    private fun showFinishedState() {
        statusText.text = "Pause complete. Still want to open Instagram?"
        statusText.setTextColor(TEXT)
        continueButton.isEnabled = true
        continueButton.alpha = 1f
        continueButton.text = "Continue to Instagram"
    }

    @SuppressLint("SetTextI18n")
    private fun buildView(
        userName: String,
        penaltySeconds: Int,
        strikes: Int,
        justPenalized: Boolean
    ): View {
        val frame = object : FrameLayout(context) {
            // Swallow Back so it can't reach Instagram underneath.
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(NIGHT)
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true // eat touches that miss our controls
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(56), dp(24), dp(32))
        }

        val greeting = if (userName.isNotBlank()) "Hi $userName." else "Hi."
        column.addView(bodyText(greeting, MUTED).apply { gravity = Gravity.CENTER })
        column.addView(TextView(context).apply {
            text = "Take a breath before Instagram"
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(24))
        })

        // Timer ring with the countdown inside
        val ringBox = FrameLayout(context)
        ring = RingView(context)
        ringBox.addView(ring, FrameLayout.LayoutParams(dp(220), dp(220)))
        timeText = TextView(context).apply {
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        ringBox.addView(timeText, FrameLayout.LayoutParams(dp(220), dp(220)))
        column.addView(ringBox, LinearLayout.LayoutParams(dp(220), dp(220)))

        statusText = bodyText("Instagram unlocks when the timer ends.", MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        column.addView(statusText)

        if (penaltySeconds > 0) {
            val msg = if (justPenalized) {
                "You left the last pause early. +${penaltySeconds}s added ($strikes skip ${plural(strikes, "attempt")})."
            } else {
                "+${penaltySeconds}s penalty from recent skip attempts. Clears after 2 hours without skipping."
            }
            column.addView(bodyText(msg, BLUSH).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }

        // Tasks
        column.addView(sectionLabel("YOUR TASKS"))
        tasksContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(card(tasksContainer), matchWidth())
        tasksContainer.addView(bodyText("Loading…", MUTED))

        // Goal (hidden until content arrives)
        val goalLabel = sectionLabel("NEXT GOAL").apply { visibility = View.GONE }
        column.addView(goalLabel)
        goalText = bodyText("", TEXT).apply {
            visibility = View.GONE
            tag = goalLabel
        }
        column.addView(goalText, matchWidth())

        // Spacer pushes buttons down on tall screens
        column.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))

        continueButton = button("Wait for the timer…", filled = true).apply {
            isEnabled = false
            alpha = 0.45f
            setOnClickListener { if (finished) onContinue() }
        }
        column.addView(continueButton, matchWidth().apply { topMargin = dp(28) })

        val close = button("Close Instagram", filled = false).apply {
            setOnClickListener { onCloseTarget() }
        }
        column.addView(close, matchWidth().apply { topMargin = dp(12) })

        scroll.addView(column, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        frame.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        return frame
    }

    // ── Small view helpers ──────────────────────────────────────

    private fun bodyText(text: String, color: Int) = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(0f, 1.15f)
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        letterSpacing = 0.08f
        setPadding(0, dp(28), 0, dp(8))
        layoutParams = matchWidth()
    }

    private fun card(child: View): View = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            setColor(SURFACE)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), BORDER)
        }
        setPadding(dp(16), dp(10), dp(16), dp(10))
        addView(child)
    }

    private fun button(label: String, filled: Boolean) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minHeight = dp(54)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            if (filled) setColor(ACCENT) else {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), BORDER)
            }
        }
        setTextColor(if (filled) NIGHT else TEXT)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun plural(n: Int, word: String) = if (n == 1) word else "${word}s"

    private fun dueLabel(millis: Long): String {
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance()
        val sameYear = target.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        val dayDiff = target.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR)
        return when {
            millis < System.currentTimeMillis() && !(sameYear && dayDiff == 0) -> "overdue"
            sameYear && dayDiff == 0 -> "today"
            sameYear && dayDiff == 1 -> "tomorrow"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
        }
    }

    /** Thin circular progress ring. */
    private class RingView(context: Context) : View(context) {
        var progress = 0f
            set(value) {
                field = value
                invalidate()
            }

        private val stroke = 8f * context.resources.displayMetrics.density
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = BORDER
        }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = ACCENT
        }
        private val bounds = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val half = stroke / 2f
            bounds.set(half, half, width - half, height - half)
            canvas.drawArc(bounds, 0f, 360f, false, track)
            canvas.drawArc(bounds, -90f, 360f * progress, false, arc)
        }
    }

    companion object {
        private const val TAG = "LockOverlay"
        private val NIGHT = Color.parseColor("#111B36")
        private val SURFACE = Color.parseColor("#1B2747")
        private val BORDER = Color.parseColor("#2E3B5E")
        private val TEXT = Color.parseColor("#F8F5FF")
        private val MUTED = Color.parseColor("#A9B1C9")
        private val ACCENT = Color.parseColor("#77CDB4")
        private val BLUSH = Color.parseColor("#F1C5CA")
    }
}
