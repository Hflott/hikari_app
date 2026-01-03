package tv.anime.app.ui

import androidx.compose.ui.unit.dp

/**
 * Home screen layout constants kept in one place.
 *
 * These are `internal` so other UI components (Hero slider, catalog rows) can share them
 * without exposing them as part of the public API.
 */
internal val HomePaddingH = 18.dp
internal val HomeSectionSpacing = 18.dp

// Keep content clear of the (collapsed) side rail icons.
internal val HomeContentInsetStart = SideMenuContentInsetStart

internal val HomeRowVerticalPadding = 6.dp
internal val HomeRowItemSpacing = 14.dp
