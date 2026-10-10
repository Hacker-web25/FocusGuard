package com.dhruv.focusguard.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MindfulMe palette.
 *
 * Raw values live here only. Screens and components must read semantic tokens
 * from [MindfulColors] via `MaterialTheme.mindful`, never these constants
 * directly, so light and dark stay in step.
 */

// ── Warm canvas ────────────────────────────────────────────────
val Ivory = Color(0xFFF8F6F0)
val WarmWhite = Color(0xFFFFFEFB)
val PureWhite = Color(0xFFFFFFFF)
val BorderWarm = Color(0xFFE9E6DE)

// ── Ink ────────────────────────────────────────────────────────
val GreenInk = Color(0xFF293B35)
val GreenInkMuted = Color(0xFF78827D)

// ── Botanical greens ───────────────────────────────────────────
val Botanical = Color(0xFF77CDB4)
val BotanicalDeep = Color(0xFF367F6D)
val Sage = Color(0xFFD5E8DA)

// ── Supporting pastels ─────────────────────────────────────────
val Lavender = Color(0xFFBBA4F1)
val PowderBlue = Color(0xFFBDD7F1)
val Blush = Color(0xFFF1C5CA)
val Peach = Color(0xFFF4DEC8)

// ── Focus / night surfaces ─────────────────────────────────────
val FocusNight = Color(0xFF111B36)
val FocusNightDeep = Color(0xFF0A1227)
val FocusNightLift = Color(0xFF1C2748)
val FocusMoon = Color(0xFFF8F5FF)
val FocusMuted = Color(0xFFA8B0C8)
val FocusBorder = Color(0xFF2A3558)

// ── Accent inks (readable text on their pastel surfaces) ───────
val LavenderInk = Color(0xFF5B3FA8)
val BlushInk = Color(0xFFB04B5A)
val PeachInk = Color(0xFFA9713C)
val BlueInk = Color(0xFF3E6D99)

// ── Priority dots (solid, tasteful — never neon) ───────────────
val DotLow = Color(0xFF77CDB4)
val DotMedium = Color(0xFFE8B583)
val DotHigh = Color(0xFFA98BE8)
val DotUrgent = Color(0xFFDE8894)

// ── Dark-mode counterparts ─────────────────────────────────────
val BotanicalLight = Color(0xFF8FDCC4)
val LavenderLight = Color(0xFFCDBBF6)
val BlushLight = Color(0xFFF5D3D7)
val PeachLight = Color(0xFFF7E7D6)

/**
 * Semantic tokens. Every screen reads from here.
 */
data class MindfulColors(
    // Canvas and surfaces
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val border: Color,
    val borderStrong: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnAccent: Color,

    // Actions
    val accent: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val onAccent: Color,

    // Feedback
    val success: Color,
    val warning: Color,
    val danger: Color,
    val disabled: Color,
    val disabledText: Color,

    // Supporting accents
    val lavender: Color,
    val powderBlue: Color,
    val blush: Color,
    val peach: Color,

    // Priority dots
    val dotLow: Color,
    val dotMedium: Color,
    val dotHigh: Color,
    val dotUrgent: Color,

    // Priority chip surfaces + their inks
    val chipLow: Color,
    val chipLowInk: Color,
    val chipMedium: Color,
    val chipMediumInk: Color,
    val chipHigh: Color,
    val chipHighInk: Color,
    val chipUrgent: Color,
    val chipUrgentInk: Color,

    // Focus / night surfaces (the pause overlay)
    val focusCanvas: Color,
    val focusCanvasDeep: Color,
    val focusSurface: Color,
    val focusBorder: Color,
    val focusText: Color,
    val focusTextMuted: Color,

    val isDark: Boolean
)

val LightMindfulColors = MindfulColors(
    canvas = Ivory,
    surface = WarmWhite,
    surfaceRaised = PureWhite,
    surfaceSunken = Color(0xFFF2EFE7),
    border = BorderWarm,
    borderStrong = Color(0xFFDAD5C9),

    textPrimary = GreenInk,
    textSecondary = GreenInkMuted,
    textOnAccent = Color(0xFF17352C),

    accent = Botanical,
    accentDeep = BotanicalDeep,
    accentSoft = Sage,
    onAccent = Color(0xFF14332A),

    success = BotanicalDeep,
    warning = PeachInk,
    danger = BlushInk,
    disabled = Color(0xFFEDEAE2),
    disabledText = Color(0xFFA8AFAA),

    lavender = Lavender,
    powderBlue = PowderBlue,
    blush = Blush,
    peach = Peach,

    dotLow = DotLow,
    dotMedium = DotMedium,
    dotHigh = DotHigh,
    dotUrgent = DotUrgent,

    chipLow = Sage,
    chipLowInk = BotanicalDeep,
    chipMedium = Peach,
    chipMediumInk = PeachInk,
    chipHigh = Color(0xFFE7DEFA),
    chipHighInk = LavenderInk,
    chipUrgent = Blush,
    chipUrgentInk = BlushInk,

    focusCanvas = FocusNight,
    focusCanvasDeep = FocusNightDeep,
    focusSurface = FocusNightLift,
    focusBorder = FocusBorder,
    focusText = FocusMoon,
    focusTextMuted = FocusMuted,

    isDark = false
)

val DarkMindfulColors = MindfulColors(
    canvas = Color(0xFF0E1730),
    surface = Color(0xFF16203C),
    surfaceRaised = FocusNightLift,
    surfaceSunken = Color(0xFF0A1227),
    border = FocusBorder,
    borderStrong = Color(0xFF3A4770),

    textPrimary = FocusMoon,
    textSecondary = FocusMuted,
    textOnAccent = Color(0xFF0A1227),

    accent = BotanicalLight,
    accentDeep = Botanical,
    accentSoft = Color(0xFF1E3A44),
    onAccent = Color(0xFF07211B),

    success = BotanicalLight,
    warning = Color(0xFFEBC08F),
    danger = Color(0xFFEFA3AD),
    disabled = Color(0xFF232E4E),
    disabledText = Color(0xFF6B7490),

    lavender = LavenderLight,
    powderBlue = Color(0xFFCFE2F6),
    blush = BlushLight,
    peach = PeachLight,

    dotLow = BotanicalLight,
    dotMedium = Color(0xFFEBC08F),
    dotHigh = LavenderLight,
    dotUrgent = Color(0xFFEFA3AD),

    chipLow = Color(0xFF1E3A44),
    chipLowInk = BotanicalLight,
    chipMedium = Color(0xFF3B3122),
    chipMediumInk = Color(0xFFEBC08F),
    chipHigh = Color(0xFF2E2950),
    chipHighInk = LavenderLight,
    chipUrgent = Color(0xFF42262C),
    chipUrgentInk = Color(0xFFEFA3AD),

    focusCanvas = FocusNight,
    focusCanvasDeep = FocusNightDeep,
    focusSurface = FocusNightLift,
    focusBorder = FocusBorder,
    focusText = FocusMoon,
    focusTextMuted = FocusMuted,

    isDark = true
)
