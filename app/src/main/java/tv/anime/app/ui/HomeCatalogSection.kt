package tv.anime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.anime.app.domain.AnimeCard

/**
 * Shared catalog row used on the Home screen.
 *
 * Extracted from HomeContent.kt to keep the main screen readable.
 */
@Composable
internal fun CatalogSection(
    title: String,
    items: List<AnimeCard>,
    onOpenDetails: (Int) -> Unit,
    onOpenMenu: () -> Unit,
    rememberLastContentFocus: (FocusRequester) -> Unit,
    rowState: LazyListState? = null,
    firstItemRequester: FocusRequester? = null,
    requestersById: MutableMap<Int, FocusRequester>? = null,
    lastFocusedId: MutableState<Int?>? = null,
    onMoveUpFromAnyItem: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HomeContentInsetStart, end = HomePaddingH)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        val tileHeight = PosterTileHeight + PosterTileTitleSpacer + PosterTileTitleHeight

        val focusSafePadV = 14.dp
        val focusSafePadH = 10.dp

        LazyRow(
            state = rowState ?: rememberLazyListState(),
            // Add extra height so the focused card can grow without being clipped.
            modifier = Modifier.height(tileHeight + (HomeRowVerticalPadding + focusSafePadV) * 2),
            contentPadding = PaddingValues(
                start = 0.dp + focusSafePadH,
                end = focusSafePadH,
                top = HomeRowVerticalPadding + focusSafePadV,
                bottom = HomeRowVerticalPadding + focusSafePadV
            ),
            horizontalArrangement = Arrangement.spacedBy(HomeRowItemSpacing)
        ) {
            itemsIndexed(
                items = items,
                key = { _, a -> a.id },
                contentType = { _, _ -> "anime-card" }
            ) { index, anime ->
                val itemRequester: FocusRequester = when {
                    index == 0 && firstItemRequester != null -> {
                        requestersById?.set(anime.id, firstItemRequester)
                        firstItemRequester
                    }
                    requestersById != null -> requestersById.getOrPut(anime.id) { FocusRequester() }
                    else -> remember(anime.id) { FocusRequester() }
                }

                val upMod = if (onMoveUpFromAnyItem != null) {
                    Modifier.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                            onMoveUpFromAnyItem.invoke(); true
                        } else false
                    }
                } else Modifier

                PosterTile(
                    anime = anime,
                    onClick = { onOpenDetails(anime.id) },
                    focusRequester = itemRequester,
                    rememberLastContentFocus = rememberLastContentFocus,
                    onFocused = { lastFocusedId?.value = anime.id },
                    modifier = Modifier
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && index == 0) {
                                onOpenMenu(); true
                            } else false
                        }
                        .then(upMod)
                )
            }
        }
    }
}

@Composable
internal fun CatalogSectionSkeleton(
    title: String,
    itemCount: Int = 12
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HomeContentInsetStart, end = HomePaddingH)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        val tileHeight = PosterTileHeight + PosterTileTitleSpacer + PosterTileTitleHeight

        val focusSafePadV = 14.dp
        val focusSafePadH = 10.dp

        LazyRow(
            state = rememberLazyListState(),
            modifier = Modifier.height(tileHeight + (HomeRowVerticalPadding + focusSafePadV) * 2),
            contentPadding = PaddingValues(
                start = 0.dp + focusSafePadH,
                end = focusSafePadH,
                top = HomeRowVerticalPadding + focusSafePadV,
                bottom = HomeRowVerticalPadding + focusSafePadV
            ),
            horizontalArrangement = Arrangement.spacedBy(HomeRowItemSpacing)
        ) {
            items(itemCount) {
                PosterTileSkeleton()
            }
        }
    }
}
