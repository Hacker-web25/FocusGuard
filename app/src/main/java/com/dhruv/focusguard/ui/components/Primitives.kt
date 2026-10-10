package com.dhruv.focusguard.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dhruv.focusguard.data.model.Priority
import com.dhruv.focusguard.ui.theme.PureWhite
import com.dhruv.focusguard.ui.theme.Radius
import com.dhruv.focusguard.ui.theme.Sizes
import com.dhruv.focusguard.ui.theme.Space
import com.dhruv.focusguard.ui.theme.mindful

/**
 * Shared UI primitives.
 *
 * Every screen composes from these, so a card, a button or an input looks and
 * behaves the same everywhere. Nothing here holds business state.
 */

/**
 * True when the user has turned animations off system-wide. Callers skip
 * ambient motion and shorten transitions rather than removing feedback.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

// ── Surfaces ───────────────────────────────────────────────────

/** The warm-paper card used across the app. */
@Composable
fun MindfulCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = Radius.card,
    background: Color? = null,
    borderColor: Color? = null,
    elevated: Boolean = true,
    contentPadding: Dp = Space.md,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = MaterialTheme.mindful
    val fill = background ?: c.surface
    val stroke = borderColor ?: c.border

    Column(
        modifier = modifier
            .then(
                if (elevated && !c.isDark) {
                    Modifier.shadow(
                        elevation = 2.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color(0x14293B35),
                        spotColor = Color(0x14293B35)
                    )
                } else Modifier
            )
            .clip(shape)
            .background(fill)
            .border(1.dp, stroke, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else Modifier
            )
            .padding(contentPadding),
        content = content
    )
}

/** Rounded icon container used in quick actions, goals and settings rows. */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.iconTile,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3f))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

// ── Headings ───────────────────────────────────────────────────

/**
 * Screen identity. A plain heading rather than a Material app bar, so every
 * screen opens with the same calm, generous title block.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = MaterialTheme.mindful
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = c.textPrimary
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Space.xxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            trailing()
        }
    }
}

/** Quiet uppercase label that opens a group of content. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val c = MaterialTheme.mindful
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color ?: c.textSecondary,
        modifier = modifier
    )
}

// ── Buttons ────────────────────────────────────────────────────

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val c = MaterialTheme.mindful
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Sizes.buttonHeight),
        shape = Radius.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent,
            contentColor = c.onAccent,
            disabledContainerColor = c.disabled,
            disabledContentColor = c.disabledText
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.iconSm))
            Spacer(Modifier.width(Space.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color? = null
) {
    val c = MaterialTheme.mindful
    val ink = contentColor ?: c.textPrimary
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Sizes.buttonHeight),
        shape = Radius.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = ink,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = c.disabledText
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.borderStrong)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.iconSm))
            Spacer(Modifier.width(Space.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Pill used for filters, quick dates and priority selection. */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedContainer: Color? = null,
    selectedContent: Color? = null,
    leadingDot: Color? = null
) {
    val c = MaterialTheme.mindful
    val container by animateColorAsState(
        targetValue = if (selected) (selectedContainer ?: c.accentSoft) else c.surface,
        animationSpec = tween(160),
        label = "chipContainer"
    )
    val ink by animateColorAsState(
        targetValue = if (selected) (selectedContent ?: c.accentDeep) else c.textSecondary,
        animationSpec = tween(160),
        label = "chipInk"
    )

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(Radius.chip)
            .background(container)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else c.border,
                shape = Radius.chip
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingDot != null) {
            Box(
                Modifier
                    .size(Sizes.dot * 0.8f)
                    .clip(Radius.chip)
                    .background(leadingDot)
            )
            Spacer(Modifier.width(Space.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Priority ───────────────────────────────────────────────────

/** Solid colour used for a priority's dot and accents. */
@Composable
fun priorityDotColor(priority: Priority): Color {
    val c = MaterialTheme.mindful
    return when (priority) {
        Priority.LOW -> c.dotLow
        Priority.MEDIUM -> c.dotMedium
        Priority.HIGH -> c.dotHigh
        Priority.URGENT -> c.dotUrgent
    }
}

/** Pastel surface + readable ink for a priority chip. */
@Composable
fun priorityChipColors(priority: Priority): Pair<Color, Color> {
    val c = MaterialTheme.mindful
    return when (priority) {
        Priority.LOW -> c.chipLow to c.chipLowInk
        Priority.MEDIUM -> c.chipMedium to c.chipMediumInk
        Priority.HIGH -> c.chipHigh to c.chipHighInk
        Priority.URGENT -> c.chipUrgent to c.chipUrgentInk
    }
}

@Composable
fun PriorityBadge(priority: Priority, modifier: Modifier = Modifier) {
    val (container, ink) = priorityChipColors(priority)
    Text(
        text = priority.label,
        style = MaterialTheme.typography.labelMedium,
        color = ink,
        modifier = modifier
            .clip(Radius.chip)
            .background(container)
            .padding(horizontal = Space.xs + 2.dp, vertical = 3.dp)
    )
}

@Composable
fun PriorityDot(priority: Priority, modifier: Modifier = Modifier, size: Dp = Sizes.dot) {
    Box(
        modifier = modifier
            .size(size)
            .clip(Radius.chip)
            .background(priorityDotColor(priority))
    )
}

// ── Progress ───────────────────────────────────────────────────

/**
 * Rounded progress track. Animates between values unless motion is reduced.
 */
@Composable
fun MindfulProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    trackColor: Color? = null,
    height: Dp = Sizes.progressTrack,
    animate: Boolean = true
) {
    val c = MaterialTheme.mindful
    val target = progress.coerceIn(0f, 1f)
    val value by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (animate) 520 else 0),
        label = "progress"
    )

    Box(
        modifier = modifier
            .height(height)
            .clip(Radius.chip)
            .background(trackColor ?: c.surfaceSunken)
    ) {
        if (value > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(value)
                    .fillMaxHeight()
                    .clip(Radius.chip)
                    .background(color ?: c.accent)
            )
        }
    }
}

// ── Completion control ─────────────────────────────────────────

/**
 * The circular complete/incomplete control on a task row.
 * Purely presentational — [onToggle] calls straight into the view model.
 */
@Composable
fun CompletionToggle(
    completed: Boolean,
    accent: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val c = MaterialTheme.mindful
    val fill by animateColorAsState(
        targetValue = if (completed) c.accent else Color.Transparent,
        animationSpec = tween(220),
        label = "toggleFill"
    )
    val ring by animateColorAsState(
        targetValue = if (completed) c.accent else accent.copy(alpha = 0.55f),
        animationSpec = tween(220),
        label = "toggleRing"
    )

    val view = LocalView.current
    val pop by animateFloatAsState(
        targetValue = if (completed) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 520f),
        label = "togglePop"
    )

    Box(
        modifier = modifier
            .size(Sizes.touchTarget)
            .clickable(
                role = Role.Checkbox,
                onClick = {
                    // A crisp confirm when ticking off, a light tick when undoing.
                    view.performHapticFeedback(
                        if (!completed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            HapticFeedbackConstants.CONFIRM
                        } else {
                            HapticFeedbackConstants.CLOCK_TICK
                        }
                    )
                    onToggle()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                }
                .clip(Radius.chip)
                .background(fill)
                .border(2.dp, ring, Radius.chip),
            contentAlignment = Alignment.Center
        ) {
            if (completed) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = contentDescription,
                    tint = c.onAccent,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// ── Inputs ─────────────────────────────────────────────────────

/**
 * Labelled soft-surface text field.
 *
 * Built on BasicTextField so the label sits above the box rather than
 * notching its border, matching the rest of the system.
 */
@Composable
fun MindfulField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    supportingText: String? = null,
    isError: Boolean = false
) {
    val c = MaterialTheme.mindful
    val interactionSource = remember { MutableInteractionSource() }
    val borderColor = when {
        isError -> c.danger
        else -> c.border
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary
        )
        Spacer(Modifier.height(Space.xs))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accentDeep),
            singleLine = singleLine,
            minLines = minLines,
            interactionSource = interactionSource,
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Sizes.fieldHeight)
                        .clip(Radius.field)
                        .background(c.surfaceRaised)
                        .border(1.dp, borderColor, Radius.field)
                        .padding(horizontal = Space.md, vertical = Space.sm),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.disabledText
                        )
                    }
                    inner()
                }
            }
        )
        if (supportingText != null) {
            Spacer(Modifier.height(Space.xxs + 2.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) c.danger else c.textSecondary
            )
        }
    }
}

/**
 * Read-only row that opens a picker — used for deadlines and goal linking.
 */
@Composable
fun SelectorRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    placeholderStyle: Boolean = false
) {
    val c = MaterialTheme.mindful
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Sizes.fieldHeight)
            .clip(Radius.field)
            .background(c.surfaceRaised)
            .border(1.dp, c.border, Radius.field)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(Sizes.icon)
            )
            Spacer(Modifier.width(Space.sm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (placeholderStyle) c.disabledText else c.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingIcon != null) {
            Icon(
                trailingIcon,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(Sizes.icon)
            )
        }
    }
}

// ── Settings row ───────────────────────────────────────────────

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color? = null,
    iconBackground: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val c = MaterialTheme.mindful
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(
            icon = icon,
            tint = iconTint ?: c.accentDeep,
            background = iconBackground ?: c.accentSoft,
            size = 36.dp
        )
        Spacer(Modifier.width(Space.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            trailing()
        }
    }
}

/** Hairline separator between settings rows. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    val c = MaterialTheme.mindful
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.md + 36.dp + Space.sm, end = Space.md)
            .height(1.dp)
            .background(c.border)
    )
}

// ── Empty state ────────────────────────────────────────────────

/** Reassuring empty state: art, a warm line, and an optional way forward. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val c = MaterialTheme.mindful
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SprigMark(modifier = Modifier.size(92.dp))
        Spacer(Modifier.height(Space.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary
        )
        Spacer(Modifier.height(Space.xxs + 2.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.md))
            SecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Small inline metric used in headers and recap-style summaries. */
@Composable
fun StatPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color? = null
) {
    val c = MaterialTheme.mindful
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = accent ?: c.textPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
    }
}
