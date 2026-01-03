package tv.anime.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import tv.anime.app.data.AnimeRepository
import tv.anime.app.data.tmdb.HeroBackdropRepository
import tv.anime.app.data.tmdb.TitleLogoRepository
import tv.anime.app.domain.AnimeDetails
import tv.anime.app.domain.Episode
import tv.anime.app.domain.EpisodeProvider
import tv.anime.app.domain.Season

@Composable
fun DetailsScreen(
    repository: AnimeRepository,
    episodeProvider: EpisodeProvider,
    anilistId: Int,
    onBack: () -> Unit,
    onPlayEpisode: (Int, Episode) -> Unit
) {
    var details by remember { mutableStateOf<AnimeDetails?>(null) }
    var seasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    var selectedSeasonId by remember { mutableStateOf<Int?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // Artwork
    var backdropUrl by remember { mutableStateOf<String?>(null) }
    var logoUrl by remember { mutableStateOf<String?>(null) }

    // Load details + seasons once per title
    LaunchedEffect(anilistId) {
        loading = true
        error = null
        details = null
        seasons = emptyList()
        selectedSeasonId = null
        episodes = emptyList()

        runCatching { repository.details(anilistId) }
            .onSuccess { details = it }
            .onFailure { error = it.message }

        runCatching { episodeProvider.getSeasons(anilistId) }
            .onSuccess { s ->
                val safe = s.ifEmpty { listOf(Season(anilistId, "Season 1")) }
                seasons = safe
                selectedSeasonId = safe.first().mediaId
            }
            .onFailure {
                seasons = listOf(Season(anilistId, "Season 1"))
                selectedSeasonId = anilistId
            }

        loading = false
    }

    // Resolve high-quality artwork once details are available.
    LaunchedEffect(details?.id) {
        val d = details ?: return@LaunchedEffect
        backdropUrl = runCatching { HeroBackdropRepository.getBackdropUrl(d) }.getOrNull()
        logoUrl = runCatching { TitleLogoRepository.getLogoUrl(d) }.getOrNull()
    }

    // Load episodes whenever the selected season changes
    LaunchedEffect(selectedSeasonId) {
        val sid = selectedSeasonId ?: return@LaunchedEffect
        episodes = emptyList()
        runCatching { episodeProvider.getEpisodes(sid) }
            .onSuccess { episodes = it }
            .onFailure { error = it.message }
    }

    Surface(
        modifier = Modifier.onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.Back) {
                onBack()
                true
            } else false
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val headerHeight = maxHeight * 0.60f

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when {
                    loading -> item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Loading…")
                        }
                    }

                    error != null -> item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Error: $error")
                        }
                    }

                    details == null -> item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Not found")
                        }
                    }

                    else -> {
                        val d = details!!

                        // Hero header (40% of screen height)
                        item {
                            DetailsHeader(
                                details = d,
                                backdropUrl = backdropUrl,
                                logoUrl = logoUrl,
                                headerHeight = headerHeight,
                                seasons = seasons,
                                selectedSeasonId = selectedSeasonId,
                                onSelectSeason = { selectedSeasonId = it },
                                firstEpisode = episodes.firstOrNull(),
                                onPlayFirst = { ep ->
                                    val sid = selectedSeasonId ?: anilistId
                                    onPlayEpisode(sid, ep)
                                }
                            )
                        }

                        item {
                            val d = details!!
                            val desc = d.description
                                ?.let { normalizeDescriptionText(it) }
                                ?.takeIf { it.isNotBlank() }

                            if (desc != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodyLarge
                                        // NOTE: no maxLines -> it expands and the page scrolls normally.
                                    )
                                }
                            }
                        }

                        // Episodes header
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Episodes",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }

                        // Episodes list
                        items(episodes) { ep ->
                            Card(
                                onClick = { onPlayEpisode(selectedSeasonId ?: anilistId, ep) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                scale = CardDefaults.scale(
                                    focusedScale = 1.02f,
                                    pressedScale = 0.99f
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Episode ${ep.number}",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        ep.title?.let { Text(it, maxLines = 1) }
                                    }
                                    Text("Play")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun DetailsHeader(
    details: AnimeDetails,
    backdropUrl: String?,
    logoUrl: String?,
    headerHeight: Dp,
    seasons: List<Season>,
    selectedSeasonId: Int?,
    onSelectSeason: (Int) -> Unit,
    firstEpisode: Episode?,
    onPlayFirst: (Episode) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val headerUrl = backdropUrl ?: details.bannerUrl ?: details.coverUrl

    val leftScrim = remember {
        Brush.horizontalGradient(
            0.0f to Color.Black.copy(alpha = 0.78f),
            0.28f to Color.Black.copy(alpha = 0.45f),
            0.55f to Color.Black.copy(alpha = 0.12f),
            1.0f to Color.Transparent
        )
    }
    val bottomScrim = remember {
        Brush.verticalGradient(
            0.00f to Color.Transparent,
            0.32f to Color.Transparent,
            0.52f to Color.Black.copy(alpha = 0.18f),
            0.66f to Color.Black.copy(alpha = 0.38f),
            0.78f to Color.Black.copy(alpha = 0.62f),
            0.88f to Color.Black.copy(alpha = 0.82f),
            1.00f to Color.Black.copy(alpha = 0.96f)
        )
    }
    val topScrim = remember {
        Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = 0.70f),
            0.20f to Color.Black.copy(alpha = 0.25f),
            0.50f to Color.Transparent,
            1.0f to Color.Transparent
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clip(RoundedCornerShape(0.dp))
    ) {
        if (headerUrl != null) {
            val (wPx, hPx) = remember(maxWidth, headerHeight, density) {
                with(density) { maxWidth.roundToPx() to headerHeight.roundToPx() }
            }
            val req = remember(headerUrl, wPx, hPx) {
                ImageRequest.Builder(context)
                    .data(headerUrl)
                    .size(wPx, hPx)
                    .precision(Precision.EXACT)
                    .crossfade(false)
                    .allowHardware(true)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }

        Box(Modifier.fillMaxSize().background(leftScrim))
        Box(Modifier.fillMaxSize().background(topScrim))
        Box(Modifier.fillMaxSize().background(bottomScrim))

        val contentPaddingStart = 24.dp
        val contentPaddingEnd = 24.dp
        val contentPaddingBottom = 24.dp

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = contentPaddingStart,
                    end = contentPaddingEnd,
                    bottom = contentPaddingBottom
                ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Poster
            val posterUrl = details.coverUrl
            if (!posterUrl.isNullOrBlank()) {
                Card(
                    onClick = { /* visual only */ },
                    modifier = Modifier.size(width = 124.dp, height = 184.dp),
                    scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
                ) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Logo or title
                if (!logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .height(104.dp)
                            .fillMaxWidth(0.62f),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = details.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Meta chips
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    details.episodes?.let { MetaChip("$it Episodes") }
                    details.format?.let { MetaChip(it.replace('_', ' ')) }
                    details.status?.let { MetaChip(it.replace('_', ' ')) }
                    details.season?.let { s ->
                        val yr = details.seasonYear?.toString()
                        MetaChip(listOfNotNull(s, yr).joinToString(" ").replace('_', ' '))
                    }
                    details.averageScore?.let { MetaChip("$it%") }
                }

                // Actions row (always visible now)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (firstEpisode != null) {
                        Button(onClick = { onPlayFirst(firstEpisode) }) { Text("Watch Now") }
                    } else {
                        OutlinedButton(onClick = { }) { Text("No episodes") }
                    }

                    SmallActionIconButton(Icons.Filled.FavoriteBorder, "Favorite") { }
                    SmallActionIconButton(Icons.Filled.BookmarkBorder, "Bookmark") { }
                    SmallActionIconButton(Icons.Filled.Share, "Share") { }
                }

                // Genres
                if (details.genres.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(end = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(details.genres.take(10)) { g -> MetaChip(g) }
                    }
                }

                // Seasons: only if more than one (removes Season 1 buttons)
                if (seasons.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(seasons) { season ->
                            val selected = season.mediaId == selectedSeasonId
                            if (selected) {
                                Button(onClick = { onSelectSeason(season.mediaId) }) {
                                    Text(season.label)
                                }
                            } else {
                                OutlinedButton(onClick = { onSelectSeason(season.mediaId) }) {
                                    Text(season.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmallActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        scale = CardDefaults.scale(focusedScale = 1.05f, pressedScale = 0.98f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }
    }
}


