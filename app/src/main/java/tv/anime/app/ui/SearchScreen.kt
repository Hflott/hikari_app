package tv.anime.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import tv.anime.app.data.AnimeRepository
import tv.anime.app.domain.AnimeCard

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    repository: AnimeRepository,
    onBack: () -> Unit,
    onOpenDetails: (Int) -> Unit,
    onOpenMenu: () -> Unit = {},
    rememberLastContentFocus: (FocusRequester) -> Unit = {}
) {
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.factory(repository))
    val state by vm.state.collectAsState()
    val pagingItems = vm.results.collectAsLazyPagingItems()

    val gridState = rememberLazyGridState()
    val searchShellRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Focus the non-IME shell; IME only opens when the user presses OK.
        searchShellRequester.requestFocus()
    }

    val voice = rememberVoiceSearchHandle(
        onSpokenQuery = { spoken ->
            vm.setQuery(spoken)
            vm.commitSearch()
            pagingItems.refresh()
        }
    )

    Surface(color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = SideMenuContentInsetStart,
                    end = DefaultPagePaddingH,
                    top = 24.dp,
                    bottom = 16.dp
                )
        ) {
            Text(
                text = "Search",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = voice.onMicClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = Color.White)
                }

                Spacer(Modifier.width(10.dp))

                TvSearchBar(
                    query = state.query,
                    onQueryChange = vm::setQuery,
                    onSearch = {
                        vm.commitSearch()
                        pagingItems.refresh()
                    },
                    onClear = vm::clear,
                    modifier = Modifier.fillMaxWidth(),
                    shellFocusRequester = searchShellRequester
                )
            }

            if (voice.state.isListening) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Listening…",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            val voiceErr = voice.state.errorMessage
            if (!voice.state.isListening && !voiceErr.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = voiceErr,
                    color = Color(0xFFFFB4AB),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            val committedLen = state.committedQuery.trim().length
            val typedLen = state.query.trim().length

            when {
                // Nothing committed yet (or too short): keep the UI stable while the user types.
                committedLen < 2 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val msg = if (typedLen < 2) {
                            "Type at least 2 characters to search"
                        } else {
                            "Press Search to run the query"
                        }
                        Text(
                            msg,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }

                pagingItems.loadState.refresh is LoadState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                pagingItems.loadState.refresh is LoadState.Error -> {
                    val err = (pagingItems.loadState.refresh as LoadState.Error).error
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Search failed", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            err.message ?: "Unknown error",
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.tv.material3.OutlinedButton(onClick = onBack) {
                            Text("Back", color = Color.White)
                        }
                    }
                }

                pagingItems.itemCount == 0 &&
                    pagingItems.loadState.refresh is LoadState.NotLoading &&
                    committedLen >= 2 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }

                else -> {
                    SearchResultsGrid(
                        pagingItems = pagingItems,
                        gridState = gridState,
                        onOpenDetails = onOpenDetails,
                        onOpenMenu = onOpenMenu,
                        rememberLastContentFocus = rememberLastContentFocus
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsGrid(
    pagingItems: LazyPagingItems<AnimeCard>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onOpenDetails: (Int) -> Unit,
    onOpenMenu: () -> Unit,
    rememberLastContentFocus: (FocusRequester) -> Unit
) {
    val focusSafePadV = 8.dp
    val spacing = 14.dp

    val startPad = SideMenuContentInsetStart
    val endPad = DefaultPagePaddingH

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Available width inside the grid content padding
        val availableWidth = maxWidth - startPad - endPad

        // Compute number of columns that fit:
        // n * tileWidth + (n - 1) * spacing <= availableWidth
        val spanCount =
            (((availableWidth + spacing) / (PosterTileWidth + spacing)).toInt())
                .coerceAtLeast(1)

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(spanCount),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = startPad,
                top = 32.dp + focusSafePadV,
                end = endPad,
                bottom = 24.dp + focusSafePadV
            ),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            items(
                count = pagingItems.itemCount,
                key = { index -> pagingItems[index]?.id ?: index },
                contentType = { "anime-card" }
            ) { index ->
                val anime = pagingItems[index]
                val col = index % spanCount

                if (anime != null) {
                    PosterTile(
                        anime = anime,
                        onClick = { onOpenDetails(anime.id) },
                        modifier = Modifier.onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && col == 0) {
                                onOpenMenu()
                                true
                            } else false
                        },
                        rememberLastContentFocus = rememberLastContentFocus
                    )
                } else {
                    PosterTileSkeleton()
                }
            }

            if (pagingItems.loadState.append is LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(12.dp))
                        Text("Loading more…", color = Color.White)
                    }
                }
            }

            if (pagingItems.loadState.append is LoadState.Error) {
                val err = (pagingItems.loadState.append as LoadState.Error).error
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = err.message ?: "Failed to load more",
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
