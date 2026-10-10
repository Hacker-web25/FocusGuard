package com.dhruv.focusguard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.dhruv.focusguard.FocusGuardApp
import com.dhruv.focusguard.MainActivity
import com.dhruv.focusguard.R
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.guard.PrefsGuardStore
import com.dhruv.focusguard.guard.overlay.byUrgency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * The home-screen widget.
 *
 * Built the old, boring way on purpose (AppWidgetProvider + RemoteViews with
 * fixed row slots) because that is what every launcher on every brand
 * renders the same: Samsung One UI, MIUI/HyperOS, ColorOS, OxygenOS,
 * Funtouch, MagicOS, Pixel. No list adapter service to bind, no WorkManager.
 *
 * Sizes: the launcher tells us the widget's size; small sizes get a compact
 * strip, bigger ones a list with as many rows as fit (up to 8).
 */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        TaskWidget.scope.launch {
            try {
                TaskWidget.push(context.applicationContext, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Resized: redraw for the new size.
        onUpdate(context, manager, intArrayOf(appWidgetId))
    }
}

/** Ticks a task from the widget. Not exported: only our own PendingIntents reach it. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskWidget.ACTION_TOGGLE) return
        val id = intent.getLongExtra(TaskWidget.EXTRA_TASK_ID, -1L)
        if (id <= 0L) return
        val pending = goAsync()
        TaskWidget.scope.launch {
            try {
                TaskWidget.toggle(context.applicationContext, id)
            } finally {
                pending.finish()
            }
        }
    }
}

object TaskWidget {

    const val ACTION_TOGGLE = "com.dhruv.focusguard.widget.TOGGLE"
    const val EXTRA_TASK_ID = "task_id"

    private const val TAG = "TaskWidget"
    private const val MAX_ROWS = 8
    /** How long a ticked task stays on the widget, checked and struck through. */
    private const val DONE_SHOW_MS = 1400L

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushLock = Mutex()

    /** Tasks just ticked on the widget → (task, show until elapsedRealtime). */
    private val recentlyDone = ConcurrentHashMap<Long, Pair<Task, Long>>()

    private val ROW = intArrayOf(R.id.w_row0, R.id.w_row1, R.id.w_row2, R.id.w_row3, R.id.w_row4, R.id.w_row5, R.id.w_row6, R.id.w_row7)
    private val HIT = intArrayOf(R.id.w_row0_hit, R.id.w_row1_hit, R.id.w_row2_hit, R.id.w_row3_hit, R.id.w_row4_hit, R.id.w_row5_hit, R.id.w_row6_hit, R.id.w_row7_hit)
    private val CHECK = intArrayOf(R.id.w_row0_check, R.id.w_row1_check, R.id.w_row2_check, R.id.w_row3_check, R.id.w_row4_check, R.id.w_row5_check, R.id.w_row6_check, R.id.w_row7_check)
    private val TITLE = intArrayOf(R.id.w_row0_title, R.id.w_row1_title, R.id.w_row2_title, R.id.w_row3_title, R.id.w_row4_title, R.id.w_row5_title, R.id.w_row6_title, R.id.w_row7_title)
    private val TITLE_DONE = intArrayOf(R.id.w_row0_title_done, R.id.w_row1_title_done, R.id.w_row2_title_done, R.id.w_row3_title_done, R.id.w_row4_title_done, R.id.w_row5_title_done, R.id.w_row6_title_done, R.id.w_row7_title_done)
    private val META = intArrayOf(R.id.w_row0_meta, R.id.w_row1_meta, R.id.w_row2_meta, R.id.w_row3_meta, R.id.w_row4_meta, R.id.w_row5_meta, R.id.w_row6_meta, R.id.w_row7_meta)
    private val META_URGENT = intArrayOf(R.id.w_row0_meta_urgent, R.id.w_row1_meta_urgent, R.id.w_row2_meta_urgent, R.id.w_row3_meta_urgent, R.id.w_row4_meta_urgent, R.id.w_row5_meta_urgent, R.id.w_row6_meta_urgent, R.id.w_row7_meta_urgent)

    // ── Keeping it fresh ────────────────────────────────────────

    /**
     * Redraw whenever tasks change anywhere in the app (task list, pause
     * screen, quick add). Called once from Application.onCreate; the guard's
     * accessibility service keeps this process alive, so the widget follows
     * along without any polling.
     */
    fun observe(app: FocusGuardApp) {
        scope.launch {
            app.repository.getActiveTasks()
                .distinctUntilChanged()
                .collect { refreshAll(app) }
        }
    }

    /** Redraw every placed widget. Safe to call from anywhere. */
    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch { pushAll(appContext) }
    }

    fun placedIds(context: Context): IntArray = try {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
    } catch (e: Exception) {
        IntArray(0)
    }

    private suspend fun pushAll(context: Context) {
        val ids = placedIds(context)
        if (ids.isEmpty()) return
        push(context, AppWidgetManager.getInstance(context), ids)
    }

    suspend fun push(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        pushLock.withLock {
            val data = try {
                load(context)
            } catch (e: Exception) {
                Log.w(TAG, "Could not read tasks", e)
                return
            }
            for (id in ids) {
                try {
                    manager.updateAppWidget(id, build(context, manager, id, data))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update widget $id", e)
                }
            }
        }
    }

    // ── Ticking from the widget ─────────────────────────────────

    suspend fun toggle(context: Context, id: Long) {
        val repo = (context.applicationContext as FocusGuardApp).repository
        val task = repo.getTaskById(id) ?: run {
            pushAll(context)    // deleted elsewhere: just drop the stale row
            return
        }
        if (task.isCompleted) {
            // Tapped again while it was still showing as done: undo.
            recentlyDone.remove(id)
            repo.markIncomplete(id)
            pushAll(context)
            return
        }
        recentlyDone[id] = task.copy(isCompleted = true) to (SystemClock.elapsedRealtime() + DONE_SHOW_MS)
        repo.markComplete(id)
        try { Haptics(context).click() } catch (_: Exception) { }
        pushAll(context)
        // Let the tick and strike-through be seen, then let it go.
        delay(DONE_SHOW_MS + 80)
        pushAll(context)
    }

    // ── Data ────────────────────────────────────────────────────

    private class Data(
        val tasks: List<Task>,       // urgency order, including just-ticked ones
        val left: Int,               // still open
        val streak: Int
    )

    private suspend fun load(context: Context): Data {
        val repo = (context.applicationContext as FocusGuardApp).repository
        val active = repo.getActiveTasksOnce()
        val now = SystemClock.elapsedRealtime()
        recentlyDone.entries.removeIf { it.value.second < now }
        val activeIds = active.mapTo(HashSet()) { it.id }
        val justDone = recentlyDone.values.map { it.first }.filter { it.id !in activeIds }
        val streak = try { PrefsGuardStore(context).streakDays() } catch (e: Exception) { 0 }
        return Data(byUrgency(active + justDone), active.size, streak)
    }

    // ── Layout ──────────────────────────────────────────────────

    private fun build(context: Context, manager: AppWidgetManager, widgetId: Int, data: Data): RemoteViews {
        val options = manager.getAppWidgetOptions(widgetId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = sizesOf(options)
            if (sizes.isNotEmpty()) {
                // Android 12+: one layout per size the launcher may show (portrait,
                // landscape); it picks the right one itself.
                return RemoteViews(sizes.associateWith { render(context, it.width, it.height, data) })
            }
        }
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 250
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 180
        return render(context, w.toFloat(), h.toFloat(), data)
    }

    private fun sizesOf(options: Bundle): List<SizeF> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        return try {
            @Suppress("DEPRECATION")
            val raw = options.get(AppWidgetManager.OPTION_APPWIDGET_SIZES) as? List<*>
            raw.orEmpty().filterIsInstance<SizeF>()
                .filter { it.width > 0f && it.height > 0f }
                .distinct()
                .take(4)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun render(context: Context, widthDp: Float, heightDp: Float, data: Data): RemoteViews =
        if (heightDp < 104f || widthDp < 150f) compact(context, widthDp, data)
        else list(context, heightDp, data)

    private fun list(context: Context, heightDp: Float, data: Data): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_tasks)
        val now = System.currentTimeMillis()

        v.setTextViewText(R.id.w_date, SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now)))
        v.setTextViewText(R.id.w_sub, subtitle(data))
        v.setOnClickPendingIntent(R.id.w_open, openApp(context))
        v.setOnClickPendingIntent(R.id.w_add, quickAdd(context))

        // Header ≈ 46dp, padding 21dp, each row 34dp, "+N more" line ≈ 18dp
        val room = heightDp - 46f - 21f
        var rows = floor(room / 34f).toInt().coerceIn(1, MAX_ROWS)
        if (data.tasks.size > rows) {
            val withFooter = floor((room - 18f) / 34f).toInt()
            if (withFooter >= 1) rows = withFooter.coerceAtMost(MAX_ROWS)
        }
        val shown = data.tasks.take(rows)
        val footerFits = room - shown.size * 34f >= 18f

        for (i in 0 until MAX_ROWS) {
            val task = shown.getOrNull(i)
            if (task == null) {
                v.setViewVisibility(ROW[i], View.GONE)
                continue
            }
            v.setViewVisibility(ROW[i], View.VISIBLE)
            val urgent = isUrgent(task, now)
            if (task.isCompleted) {
                v.setImageViewResource(CHECK[i], R.drawable.widget_ring_done)
                v.setViewVisibility(TITLE[i], View.GONE)
                v.setViewVisibility(TITLE_DONE[i], View.VISIBLE)
                v.setTextViewText(TITLE_DONE[i], struck(task.title))
                v.setViewVisibility(META[i], View.GONE)
                v.setViewVisibility(META_URGENT[i], View.GONE)
            } else {
                v.setImageViewResource(CHECK[i], if (urgent) R.drawable.widget_ring_urgent else R.drawable.widget_ring)
                v.setViewVisibility(TITLE[i], View.VISIBLE)
                v.setViewVisibility(TITLE_DONE[i], View.GONE)
                v.setTextViewText(TITLE[i], task.title)
                val chip = chipFor(task, now)
                v.setViewVisibility(META[i], if (chip != null && !chip.second) View.VISIBLE else View.GONE)
                v.setViewVisibility(META_URGENT[i], if (chip != null && chip.second) View.VISIBLE else View.GONE)
                if (chip != null) v.setTextViewText(if (chip.second) META_URGENT[i] else META[i], chip.first)
            }
            v.setOnClickPendingIntent(HIT[i], toggleIntent(context, task.id))
            v.setOnClickPendingIntent(ROW[i], editTask(context, task.id))
        }

        val hidden = data.tasks.size - shown.size
        if (hidden > 0 && footerFits) {
            v.setViewVisibility(R.id.w_more, View.VISIBLE)
            v.setTextViewText(R.id.w_more, "+$hidden more  ›")
            v.setOnClickPendingIntent(R.id.w_more, openApp(context))
        } else {
            v.setViewVisibility(R.id.w_more, View.GONE)
        }

        v.setViewVisibility(R.id.w_empty, if (data.tasks.isEmpty()) View.VISIBLE else View.GONE)
        v.setOnClickPendingIntent(R.id.w_empty, quickAdd(context))
        return v
    }

    private fun compact(context: Context, widthDp: Float, data: Data): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_compact)
        val now = System.currentTimeMillis()
        val next = data.tasks.firstOrNull { !it.isCompleted }
        v.setTextViewText(R.id.c_count, data.left.toString())
        v.setTextViewText(R.id.c_label, if (data.left == 0) "All clear ✨" else if (data.left == 1) "1 task left" else "${data.left} tasks left")
        val urgent = next != null && isUrgent(next, now)
        v.setViewVisibility(R.id.c_task, if (next != null && !urgent) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.c_task_urgent, if (urgent) View.VISIBLE else View.GONE)
        if (next != null) v.setTextViewText(if (urgent) R.id.c_task_urgent else R.id.c_task, next.title)
        // 1x1 and very narrow: just the number and the + button
        v.setViewVisibility(R.id.c_text, if (widthDp < 120f) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.c_count, if (widthDp < 70f) View.GONE else View.VISIBLE)
        v.setOnClickPendingIntent(R.id.c_open, if (next != null) editTask(context, next.id) else openApp(context))
        v.setOnClickPendingIntent(R.id.c_add, quickAdd(context))
        return v
    }

    private fun subtitle(data: Data): String {
        val left = when (data.left) {
            0 -> "Nothing left"
            1 -> "1 left"
            else -> "${data.left} left"
        }
        return if (data.streak > 0) "$left · 🔥 ${data.streak}-day streak" else left
    }

    private fun struck(text: String): CharSequence =
        SpannableString(text).apply { setSpan(StrikethroughSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }

    // ── Urgency (same rules as the pause screen) ────────────────

    private const val DAY = 24 * 60 * 60 * 1000L

    private fun endOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    private fun isUrgent(task: Task, now: Long): Boolean {
        val d = task.deadline
        if (task.priority == Priority.URGENT) return true
        return d != null && d <= endOfToday() + DAY && d - now < 3 * DAY
    }

    /** Label and whether it is urgent-red, or null for no chip. */
    private fun chipFor(task: Task, now: Long): Pair<String, Boolean>? {
        val d = task.deadline
        if (d == null) return if (task.priority == Priority.URGENT) "Urgent" to true else null
        val todayEnd = endOfToday()
        return when {
            d < now -> "Overdue" to true
            d <= todayEnd -> "Today" to true
            d <= todayEnd + DAY -> "Tomorrow" to (task.priority == Priority.URGENT)
            d - now < 7 * DAY -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(d)) to (task.priority == Priority.URGENT)
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(d)) to (task.priority == Priority.URGENT)
        }
    }

    // ── Taps ────────────────────────────────────────────────────

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        FLAGS
    )

    fun quickAddIntent(context: Context): Intent =
        Intent(context, QuickAddActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    private fun quickAdd(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 2, quickAddIntent(context), FLAGS)

    private fun editTask(context: Context, id: Long): PendingIntent = PendingIntent.getActivity(
        context, 3,
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("focusguard://task/edit/$id"))   // makes each row's intent distinct
            .putExtra(MainActivity.EXTRA_EDIT_TASK, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        FLAGS
    )

    private fun toggleIntent(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 4,
        Intent(context, WidgetActionReceiver::class.java)
            .setAction(ACTION_TOGGLE)
            .setData(Uri.parse("focusguard://task/toggle/$id"))
            .putExtra(EXTRA_TASK_ID, id),
        FLAGS
    )
}
