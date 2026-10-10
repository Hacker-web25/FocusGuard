package com.dhruv.focusguard.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhruv.focusguard.FocusGuardApp
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.data.model.Task
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.ui.components.PrimaryButton
import com.dhruv.focusguard.ui.theme.FocusGuardTheme
import com.dhruv.focusguard.ui.theme.mindful
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The widget's "+": a see-through window over the home screen with a lined
 * notepad. Type, one task per line, tap Add. Tapping outside keeps what you
 * typed for next time instead of throwing it away.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as FocusGuardApp).repository

        setContent {
            FocusGuardTheme(paintSystemBars = false) {
                QuickAddSheet(
                    initial = repo.quickAddDraft,
                    onSave = { tasks ->
                        repo.quickAddDraft = ""
                        // App-wide scope: the save finishes even as this window closes.
                        TaskWidget.scope.launch {
                            repo.addTasks(tasks)
                            TaskWidget.refreshAll(applicationContext)
                        }
                    },
                    onKeepDraft = { draft -> repo.quickAddDraft = draft },
                    onClosed = {
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                )
            }
        }
    }
}

private enum class Due(val label: String) { None("No date"), Today("Today"), Tomorrow("Tomorrow") }

/** One task per line. "- ", "• ", "1." and "[ ]" are stripped; a leading "!" marks it urgent. */
internal fun parseQuickAdd(text: String, allUrgent: Boolean, deadline: Long?): List<Task> =
    text.lines().mapNotNull { raw ->
        // Strip list markers, possibly stacked ("2) [ ] Book dentist")
        var t = raw.trim()
        while (true) {
            val stripped = t.replace(BULLET, "").trim()
            if (stripped == t) break
            t = stripped
        }
        var urgent = allUrgent
        if (t.startsWith("!")) {
            urgent = true
            t = t.trimStart('!').trim()
        }
        if (t.isBlank()) null
        else Task(
            title = t.take(200),
            priority = if (urgent) Priority.URGENT else Priority.MEDIUM,
            deadline = deadline
        )
    }

// A marker needs a space after it, so "-5 push ups" and "1.5 hours" stay intact.
private val BULLET = Regex("""^([-*•·▪◦]\s+|\d{1,2}[.)]\s+|\[[ xX]?\]\s*)""")

private fun endOfDay(plusDays: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, plusDays)
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun QuickAddSheet(
    initial: String,
    onSave: (List<Task>) -> Unit,
    onKeepDraft: (String) -> Unit,
    onClosed: () -> Unit
) {
    val c = MaterialTheme.mindful
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val enter = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var urgent by remember { mutableStateOf(false) }
    var due by remember { mutableStateOf(Due.None) }

    val deadline = when (due) {
        Due.None -> null
        Due.Today -> endOfDay(0)
        Due.Tomorrow -> endOfDay(1)
    }
    val parsed = remember(value.text, urgent, due) { parseQuickAdd(value.text, urgent, deadline) }

    LaunchedEffect(Unit) {
        launch { enter.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f)) }
        delay(120)
        try { focus.requestFocus() } catch (_: Exception) { }
        keyboard?.show()
    }

    fun close(save: Boolean) {
        if (closing) return
        closing = true
        if (save) {
            haptics.success()
            onSave(parsed)
        } else {
            onKeepDraft(value.text)
        }
        keyboard?.hide()
        scope.launch {
            enter.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            onClosed()
        }
    }

    BackHandler { close(save = false) }

    val density = LocalDensity.current
    val travel = with(density) { 360.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f * enter.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close(save = false) }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(10.dp)
                .graphicsLayer {
                    translationY = (1f - enter.value) * travel
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .shadow(24.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(c.surface)
                // Taps on the card itself must not count as "outside"
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick add", style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text("one task per line", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Spacer(Modifier.height(10.dp))

            // The notepad: ruled lines behind the text, like a page.
            val lineHeight = 30.sp
            val lineColor = c.border
            val marginColor = Color(0x55E2304F)
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = TextStyle(fontSize = 17.sp, lineHeight = lineHeight, color = c.textPrimary),
                cursorBrush = SolidColor(c.accentDeep),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 270.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceSunken)
                    .drawBehind {
                        val step = lineHeight.toPx()
                        var y = 8.dp.toPx() + step * 0.84f
                        while (y < size.height) {
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                            y += step
                        }
                        drawLine(marginColor, Offset(26.dp.toPx(), 0f), Offset(26.dp.toPx(), size.height), strokeWidth = 1.dp.toPx())
                    }
                    .padding(start = 36.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (value.text.isEmpty()) {
                            Text(
                                "Call the bank\n!Submit the assignment\nBuy groceries",
                                style = TextStyle(fontSize = 17.sp, lineHeight = lineHeight, color = c.textSecondary.copy(alpha = 0.55f))
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tip: start a line with ! to make that one urgent.",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Toggle("⚡ Urgent", urgent, Color(0xFFFFDCE2), Color(0xFFD21A45)) { haptics.tick(); urgent = !urgent }
                Toggle("Today", due == Due.Today, c.accentSoft, c.accentDeep) {
                    haptics.tick(); due = if (due == Due.Today) Due.None else Due.Today
                }
                Toggle("Tomorrow", due == Due.Tomorrow, c.accentSoft, c.accentDeep) {
                    haptics.tick(); due = if (due == Due.Tomorrow) Due.None else Due.Tomorrow
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = when (parsed.size) {
                    0 -> "Add tasks"
                    1 -> "Add 1 task"
                    else -> "Add ${parsed.size} tasks"
                },
                onClick = { if (parsed.isNotEmpty()) close(save = true) },
                enabled = parsed.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Toggle(text: String, on: Boolean, onBg: Color, onInk: Color, onClick: () -> Unit) {
    val c = MaterialTheme.mindful
    val bg by animateColorAsState(if (on) onBg else c.surface, tween(160), label = "tbg")
    val ink by animateColorAsState(if (on) onInk else c.textSecondary, tween(160), label = "tink")
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (on) Color.Transparent else c.border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = ink, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}
