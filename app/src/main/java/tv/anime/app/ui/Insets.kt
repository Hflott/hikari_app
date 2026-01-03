package tv.anime.app.ui

import androidx.compose.ui.unit.dp

/**
 * Shared layout insets to keep content clear of the collapsed side rail.
 */
internal val SideRailWidth = 62.dp
internal val DefaultPagePaddingH = 18.dp

/** Left padding to avoid overlap with the (collapsed) side menu rail. */
internal val SideMenuContentInsetStart = SideRailWidth + DefaultPagePaddingH
