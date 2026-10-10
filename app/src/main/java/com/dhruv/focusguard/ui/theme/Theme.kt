package com.dhruv.focusguard.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Semantic MindfulMe tokens, available anywhere under [FocusGuardTheme]
 * as `MaterialTheme.mindful`.
 */
val LocalMindfulColors = staticCompositionLocalOf { LightMindfulColors }

val MaterialTheme.mindful: MindfulColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMindfulColors.current

private val AppShapes = Shapes(
    extraSmall = Radius.field,
    small = Radius.field,
    medium = Radius.button,
    large = Radius.card,
    extraLarge = Radius.cardLarge
)

/**
 * Material's own scheme is mapped from the same tokens so stock controls
 * (switches, sliders, dialogs, menus, text selection) inherit the palette
 * instead of falling back to purple defaults.
 */
private fun lightScheme(c: MindfulColors) = lightColorScheme(
    primary = c.accentDeep,
    onPrimary = PureWhite,
    primaryContainer = c.accentSoft,
    onPrimaryContainer = c.accentDeep,
    secondary = c.lavender,
    onSecondary = LavenderInk,
    secondaryContainer = c.lavender,
    onSecondaryContainer = LavenderInk,
    tertiary = c.powderBlue,
    onTertiary = BlueInk,
    background = c.canvas,
    onBackground = c.textPrimary,
    surface = c.surface,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceSunken,
    onSurfaceVariant = c.textSecondary,
    outline = c.borderStrong,
    outlineVariant = c.border,
    error = c.danger,
    onError = PureWhite,
    errorContainer = c.blush,
    onErrorContainer = BlushInk,
    scrim = GreenInk
)

private fun darkScheme(c: MindfulColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = c.onAccent,
    primaryContainer = c.accentSoft,
    onPrimaryContainer = c.accent,
    secondary = c.lavender,
    onSecondary = FocusNightDeep,
    secondaryContainer = c.surfaceRaised,
    onSecondaryContainer = c.lavender,
    tertiary = c.powderBlue,
    onTertiary = FocusNightDeep,
    background = c.canvas,
    onBackground = c.textPrimary,
    surface = c.surface,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceSunken,
    onSurfaceVariant = c.textSecondary,
    outline = c.borderStrong,
    outlineVariant = c.border,
    error = c.danger,
    onError = FocusNightDeep,
    errorContainer = c.chipUrgent,
    onErrorContainer = c.chipUrgentInk,
    scrim = FocusNightDeep
)

@Composable
fun FocusGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paintSystemBars: Boolean = true,
    content: @Composable () -> Unit
) {
    val mindfulColors = if (darkTheme) DarkMindfulColors else LightMindfulColors
    val colorScheme = if (darkTheme) darkScheme(mindfulColors) else lightScheme(mindfulColors)

    val view = LocalView.current
    // Off for see-through windows (quick add over the home screen).
    if (!view.isInEditMode && paintSystemBars) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = mindfulColors.canvas.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = mindfulColors.canvas.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalMindfulColors provides mindfulColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * Night theme used by the pause overlay. It is always dark regardless of the
 * system setting, because the overlay is meant to read as a different, calmer
 * place than the rest of the app.
 */
@Composable
fun FocusNightTheme(content: @Composable () -> Unit) = FocusGuardTheme(darkTheme = true, content = content)
