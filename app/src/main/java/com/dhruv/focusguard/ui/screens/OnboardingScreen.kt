package com.dhruv.focusguard.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.guard.Protection
import com.dhruv.focusguard.ui.components.MindfulCard
import com.dhruv.focusguard.ui.components.MindfulField
import com.dhruv.focusguard.ui.components.PrimaryButton
import com.dhruv.focusguard.ui.components.ScreenHeader
import com.dhruv.focusguard.ui.components.SecondaryButton
import com.dhruv.focusguard.ui.components.SectionLabel
import com.dhruv.focusguard.ui.components.openAccessibilitySettings
import com.dhruv.focusguard.ui.components.openAppDetails
import com.dhruv.focusguard.ui.components.openMiuiAutostart
import com.dhruv.focusguard.ui.components.rememberProtectionState
import com.dhruv.focusguard.ui.components.requestBatteryExemption
import com.dhruv.focusguard.ui.components.Calm
import com.dhruv.focusguard.ui.components.CalmButton
import com.dhruv.focusguard.ui.components.CalmText
import com.dhruv.focusguard.ui.components.PermissionList
import com.dhruv.focusguard.ui.components.SafetyHero
import com.dhruv.focusguard.ui.components.StepStatus
import com.dhruv.focusguard.ui.components.TransparencyCard
import com.dhruv.focusguard.ui.components.TrustRow
import com.dhruv.focusguard.ui.components.calmBackground
import com.dhruv.focusguard.ui.components.rememberPermSteps
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel

/**
 * First run: (1) your name, (2) the permissions that keep the guard alive.
 * The app can't be used until the accessibility service is actually running.
 */
@Composable
fun OnboardingScreen(viewModel: TaskViewModel, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(viewModel.userName) }

    val c = MaterialTheme.mindful
    Box(
        modifier = Modifier
            .fillMaxSize()
            .calmBackground()
    ) {
        if (step == 0) {
            NameStep(
                name = name,
                onNameChange = { name = it },
                onContinue = {
                    viewModel.userName = name
                    step = 1
                }
            )
        } else {
            PermissionsStep(
                onBack = { step = 0 },
                onFinish = {
                    viewModel.onboardingDone = true
                    onFinished()
                }
            )
        }
    }
}

@Composable
private fun NameStep(name: String, onNameChange: (String) -> Unit, onContinue: () -> Unit) {
    val c = MaterialTheme.mindful
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter, vertical = Space.lg)
    ) {
        StepIndicator(current = 0)
        Spacer(Modifier.height(Space.lg))
        ScreenHeader(
            title = "Welcome to FocusGuard",
            subtitle = "Every time you open Instagram, FocusGuard pauses it and shows " +
                "your tasks first. Let's set it up."
        )
        Spacer(Modifier.height(Space.xl))
        MindfulField(
            label = "What should we call you?",
            value = name,
            onValueChange = onNameChange,
            placeholder = "Your first name",
            supportingText = "Shown on the pause screen."
        )
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(
            text = "Continue",
            onClick = onContinue,
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "Step 1 of 2",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PermissionsStep(onBack: () -> Unit, onFinish: () -> Unit) {
    val steps = rememberPermSteps()
    val running = steps.firstOrNull { it.key == "accessibility" }?.status == StepStatus.Done

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        StepIndicator(current = 1)
        Spacer(Modifier.height(14.dp))
        Text("Let\u2019s keep FocusGuard on", style = CalmText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Phones love to switch apps like this off. These steps stop that. Each one says exactly why, and what it can\u2019t do.",
            style = CalmText, fontSize = 14.sp, color = Calm.Sub, lineHeight = 20.sp
        )
        Spacer(Modifier.height(18.dp))
        SafetyHero(steps)
        Spacer(Modifier.height(12.dp))
        TrustRow()
        Spacer(Modifier.height(20.dp))
        PermissionList(steps)
        Spacer(Modifier.height(20.dp))
        TransparencyCard()
        Spacer(Modifier.height(22.dp))
        CalmButton(
            text = if (running) "Start using FocusGuard" else "Finish step 2 to continue",
            filled = true,
            enabled = running,
            onClick = { if (running) onFinish() }
        )
        Spacer(Modifier.height(10.dp))
        CalmButton(text = "Back", filled = false, onClick = onBack)
        Spacer(Modifier.height(10.dp))
        Text(
            "Step 2 of 2",
            style = CalmText, fontSize = 12.sp, color = Calm.Faint,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

private enum class Status { Done, Required, Recommended }

@Composable
private fun PermissionItem(
    number: Int,
    title: String,
    body: String,
    status: Status?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    code: String? = null
) {
    val c = MaterialTheme.mindful
    MindfulCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (status == Status.Done) c.accentDeep else c.surfaceSunken,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (status == Status.Done) "✓" else number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (status == Status.Done) c.onAccent else c.textPrimary
                )
            }
            Spacer(Modifier.width(Space.sm))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.weight(1f)
            )
            when (status) {
                Status.Done -> StatusText("Done", c.success)
                Status.Required -> StatusText("Required", c.danger)
                Status.Recommended -> StatusText("Recommended", c.textSecondary)
                null -> Unit
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        if (code != null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                code,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = c.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surfaceSunken, MaterialTheme.shapes.small)
                    .padding(Space.xs)
            )
        }
        if (actionLabel != null && onAction != null && status != Status.Done) {
            Spacer(Modifier.height(Space.sm))
            SecondaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    Spacer(Modifier.height(Space.sm))
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun StepIndicator(current: Int) {
    val c = MaterialTheme.mindful
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        repeat(2) { i ->
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .width(if (i == current) 32.dp else 16.dp)
                    .background(
                        if (i <= current) c.accentDeep else c.border,
                        CircleShape
                    )
            )
        }
    }
    SectionLabel(if (current == 0) "ABOUT YOU" else "PERMISSIONS", modifier = Modifier.padding(top = Space.sm))
}
