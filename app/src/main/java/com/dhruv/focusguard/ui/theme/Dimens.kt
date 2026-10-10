package com.dhruv.focusguard.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Spacing, radii and sizing scale.
 *
 * One scale for the whole app so cards, inputs, buttons and sheets share
 * the same rhythm. Steps are 4 / 8 / 12 / 16 / 24 / 32.
 */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Horizontal page gutter, shared by every screen. */
    val gutter = 20.dp
}

object Radius {
    val chip = RoundedCornerShape(999.dp)
    val field = RoundedCornerShape(14.dp)
    val button = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(20.dp)
    val cardLarge = RoundedCornerShape(26.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

object Sizes {
    /** Minimum comfortable touch target. */
    val touchTarget = 48.dp
    val fieldHeight = 56.dp
    val buttonHeight = 54.dp
    val iconSm = 18.dp
    val icon = 22.dp
    val iconLg = 26.dp
    val iconTile = 40.dp
    val progressTrack = 8.dp
    val dot = 10.dp

    /** Breathing room above the bottom bar so content never hides behind it. */
    val bottomBarClearance = 96.dp
}
