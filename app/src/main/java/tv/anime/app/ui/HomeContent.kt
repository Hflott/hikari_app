package tv.anime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

@Composable
fun HomeContent(
    state: HomeState,
    onRefresh: () -> Unit,
    onOpenMenu: () -> Unit,
    rememberLastContentFocus: (FocusRequester) -> Unit,
    contentRootFocusRequester: FocusRequester,
    onOpenDetails: (Int) -> Unit
) {
    // Focus targets
    val heroRequester = contentRootFocusRequester

    // Scroll state for the home feed (hero + sections). Needed so DPAD-UP can reliably
    // snap the list back to the hero before requesting focus.
    val columnState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Recent row focus routing
    val recentRowState = rememberLazyListState()
    val recentFirstItemRequester = remember { FocusRequester() }

    // Track last-focused item in Recent and keep requesters by id
    val recentLastFocusedId = remember { mutableStateOf<Int?>(null) }
    val recentRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }

    var heroFocused by remember { mutableStateOf(false) }

    LaunchedEffect(heroFocused) {
        if (heroFocused) {
            val needsScroll =
                columnState.firstVisibleItemIndex != 0 || columnState.firstVisibleItemScrollOffset != 0

            if (needsScroll) {
                // Smoothly scroll the feed so the hero is fully visible.
                columnState.animateScrollToItem(0, 0)
            }
        }
    }

    Surface {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Hero should be wide, but not full-height: use top 70% of the viewport.
            val heroHeight = maxHeight * 0.70f

            LazyColumn(
                state = columnState,
                modifier = Modifier.fillMaxSize(),
                // TV "safe area": keep content slightly away from screen edges.
                // Also reduces the chance that the hero appears visually "cut" at the top
                // due to overscan or focus-driven bring-into-view adjustments.
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(HomeSectionSpacing)
            ) {
                if (!state.isLoading && state.error == null && state.hero.isNotEmpty()) {
                    item {
                        HeroBannerSlider(
                            items = state.hero,
                            onOpenDetails = onOpenDetails,
                            onRefresh = onRefresh,
                            onOpenMenu = onOpenMenu,
                            heroRequester = heroRequester,
                            homeListState = columnState,
                            rememberLastContentFocus = rememberLastContentFocus,
                            onHeroFocusChanged = { focused ->
                                heroFocused = focused
                            },
                            heroHeight = heroHeight,
                            onMoveDown = {
                                val notScrolled =
                                    recentRowState.firstVisibleItemIndex == 0 &&
                                        recentRowState.firstVisibleItemScrollOffset == 0

                                if (notScrolled) {
                                    recentFirstItemRequester.requestFocus()
                                } else {
                                    val lastId = recentLastFocusedId.value
                                    val fr = lastId?.let { recentRequesters[it] }
                                    (fr ?: recentFirstItemRequester).requestFocus()
                                }
                            }
                        )
                    }
                }

                when {
                    state.isLoading -> {
                        item { CatalogSectionSkeleton(title = "Recent episodes") }
                        item { CatalogSectionSkeleton(title = "Trending") }
                        item { CatalogSectionSkeleton(title = "Popular") }
                    }
                    state.error != null -> item { Text("Error: ${state.error}") }
                    else -> {
                        item {
                            CatalogSection(
                                title = "Recent episodes",
                                items = state.recent,
                                onOpenDetails = onOpenDetails,
                                onOpenMenu = onOpenMenu,
                                rememberLastContentFocus = rememberLastContentFocus,
                                rowState = recentRowState,
                                firstItemRequester = recentFirstItemRequester,
                                requestersById = recentRequesters,
                                lastFocusedId = recentLastFocusedId,
                                onMoveUpFromAnyItem = {
                                    scope.launch {
                                        // Hard-snap to the hero before requesting focus.
                                        // Using animation here tends to fight focus-driven
                                        // bring-into-view adjustments on some Android TV
                                        // devices and can look like the hero "slides down".
                                        columnState.animateScrollToItem(0, 0)
                                        heroRequester.requestFocus()
                                    }
                                }
                            )
                        }

                        item {
                            CatalogSection(
                                title = "Trending",
                                items = state.trending,
                                onOpenDetails = onOpenDetails,
                                onOpenMenu = onOpenMenu,
                                rememberLastContentFocus = rememberLastContentFocus
                            )
                        }

                        item {
                            CatalogSection(
                                title = "Popular",
                                items = state.popular,
                                onOpenDetails = onOpenDetails,
                                onOpenMenu = onOpenMenu,
                                rememberLastContentFocus = rememberLastContentFocus
                            )
                        }
                    }
                }
            }
        }
    }
}
