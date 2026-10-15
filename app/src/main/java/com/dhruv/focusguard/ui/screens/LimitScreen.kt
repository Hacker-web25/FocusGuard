package com.dhruv.focusguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.guard.GuardEngine
import com.dhruv.focusguard.guard.Haptics
import com.dhruv.focusguard.guard.PrefsGuardStore
import com.dhruv.focusguard.ui.components.DayRing
import com.dhruv.focusguard.ui.components.MindfulCard
import com.dhruv.focusguard.ui.components.ScreenHeader
import com.dhruv.focusguard.ui.components.SectionLabel
import com.dhruv.focusguard.ui.components.SelectableChip
import com.dhruv.focusguard.ui.components.rememberResumeTick
import com.dhruv.focusguard.ui.theme.Radius
import com.dhruv.focusguard.ui.theme.Sizes
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.delay

/**
 * The daily budget and the long-scroll cool-down.
 *
 * Both are optional and both are hard: unlike the pause, there is no
 * countdown to sit through — the app simply does not open. The only rule
 * that isn't editable on the spot is raising a budget that today has
 * already spent, because that would make the limit meaningless.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LimitScreen(viewModel: TaskViewModel) {
    val c = MaterialTheme.mindful
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val store = remember { PrefsGuardStore(context) }
    val engine = remember { GuardEngine(store) }
    val resume = rememberResumeTick()

    var limit by remember { mutableIntStateOf(viewModel.dailyLimitMinutes) }
    var sessionLimit by remember { mutableIntStateOf(viewModel.sessionLimitMinutes) }
    var cooldown by remember { mutableIntStateOf(viewModel.cooldownMinutes) }

    // A one-second clock, only while a cool-down is actually running.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val cooldownUntil = engine.cooldownUntil(now)
    LaunchedEffect(resume, cooldownUntil > 0L) {
        while (cooldownUntil > 0L) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }

    val used = remember(resume, now) { engine.usedTodayMinutes(System.currentTimeMillis()) }
    val spentToday = limit > 0 && used >= limit

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter)
            .padding(top = Space.md, bottom = Sizes.bottomBarClearance)
    ) {
        ScreenHeader(
            title = "Daily limit",
            subtitle = "An optional ceiling on Instagram. When it's reached, the app stops opening until tomorrow."
        )
        Spacer(Modifier.height(Space.md))

        // ── Where today stands ─────────────────────────────────
        MindfulCard(modifier = Modifier.fillMaxWidth(), shape = Radius.cardLarge) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayRing(
                    done = if (limit > 0) used.coerceAtMost(limit) else used,
                    total = if (limit > 0) limit else (used + 30).coerceAtLeast(1),
                    size = 62.dp,
                    showCount = false
                )
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        "$used min",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (spentToday) c.danger else c.textPrimary
                    )
                    Text(
                        when {
                            limit <= 0 -> "on Instagram today · no limit set"
                            spentToday -> "today's limit of $limit min is spent"
                            else -> "of $limit min today · ${limit - used} left"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }
        }

        // ── A cool-down in progress ────────────────────────────
        AnimatedVisibility(
            visible = cooldownUntil > 0L,
            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
        ) {
            Column {
                Spacer(Modifier.height(Space.sm))
                MindfulCard(
                    modifier = Modifier.fillMaxWidth(),
                    background = c.peach.copy(alpha = if (c.isDark) 0.20f else 0.65f),
                    borderColor = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = c.warning)
                        Spacer(Modifier.width(Space.sm))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Cooling down",
                                style = MaterialTheme.typography.titleSmall,
                                color = c.textPrimary
                            )
                            Text(
                                "Instagram opens again in ${mmss(cooldownUntil - now)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.lg))

        // ── The budget ─────────────────────────────────────────
        SectionLabel("Minutes per day")
        Spacer(Modifier.height(Space.xs))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            for (option in LIMIT_OPTIONS) {
                SelectableChip(
                    text = if (option == 0) "No limit" else "$option min",
                    selected = limit == option,
                    onClick = {
                        if (spentToday) return@SelectableChip
                        haptics.tick()
                        limit = option
                        viewModel.dailyLimitMinutes = option
                    }
                )
            }
        }

        if (spentToday) {
            Spacer(Modifier.height(Space.sm))
            MindfulCard(
                modifier = Modifier.fillMaxWidth(),
                background = c.surfaceSunken,
                borderColor = androidx.compose.ui.graphics.Color.Transparent
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Lock, contentDescription = null, tint = c.textSecondary,
                        modifier = Modifier.size(Sizes.iconSm)
                    )
                    Spacer(Modifier.width(Space.xs))
                    Text(
                        "Today's budget is already spent, so it's fixed until midnight. " +
                            "A limit you could lift the moment it bites wouldn't be a limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.lg))

        // ── The cool-down ──────────────────────────────────────
        SectionLabel("Long-scroll cool-down")
        Spacer(Modifier.height(Space.xs))
        MindfulCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = c.accentDeep)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Break up long sessions",
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary
                    )
                    Text(
                        if (sessionLimit > 0) {
                            "$sessionLimit min of scrolling earns a $cooldown min break."
                        } else {
                            "Off — sessions can run as long as you like."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
                Switch(
                    checked = sessionLimit > 0,
                    onCheckedChange = { on ->
                        haptics.click()
                        sessionLimit = if (on) com.dhruv.focusguard.data.repository.TaskRepository.DEFAULT_SESSION_LIMIT else 0
                        viewModel.sessionLimitMinutes = sessionLimit
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.onAccent,
                        checkedTrackColor = c.accentDeep,
                        uncheckedTrackColor = c.surfaceSunken
                    )
                )
            }

            AnimatedVisibility(
                visible = sessionLimit > 0,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
            ) {
                Column {
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "After this long in one go",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                    Spacer(Modifier.height(Space.xs))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        for (option in SESSION_OPTIONS) {
                            SelectableChip(
                                text = "$option min",
                                selected = sessionLimit == option,
                                onClick = {
                                    haptics.tick()
                                    sessionLimit = option
                                    viewModel.sessionLimitMinutes = option
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "Wait this long before it opens again",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                    Spacer(Modifier.height(Space.xs))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        for (option in COOLDOWN_OPTIONS) {
                            SelectableChip(
                                text = "$option min",
                                selected = cooldown == option,
                                onClick = {
                                    haptics.tick()
                                    cooldown = option
                                    viewModel.cooldownMinutes = option
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.lg))

        // ── How it counts ──────────────────────────────────────
        MindfulCard(
            modifier = Modifier.fillMaxWidth(),
            background = c.surfaceSunken,
            borderColor = androidx.compose.ui.graphics.Color.Transparent
        ) {
            Text("How this counts", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(Space.xs))
            for (line in listOf(
                "Only time with Instagram actually on screen counts — not the pause, and not time in other apps.",
                "A break of two minutes or more starts the cool-down clock again from zero.",
                "Both limits reset at midnight, on your phone, with nothing sent anywhere.",
                "Force-stopping FocusGuard mid-scroll still counts: the time is billed when it restarts."
            )) {
                Row(Modifier.padding(top = Space.xxs)) {
                    Text("·  ", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Text(line, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
        }

        Spacer(Modifier.height(Space.md))
        Text(
            "Set it to what you'd be glad to have spent, not what feels generous.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Space.lg))
    }
}

private val LIMIT_OPTIONS = listOf(0, 10, 15, 20, 30, 45, 60, 90, 120)
private val SESSION_OPTIONS = listOf(5, 10, 15, 20, 30)
private val COOLDOWN_OPTIONS = listOf(5, 10, 15, 30)

private fun mmss(ms: Long): String {
    val s = (ms.coerceAtLeast(0L) + 999) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
