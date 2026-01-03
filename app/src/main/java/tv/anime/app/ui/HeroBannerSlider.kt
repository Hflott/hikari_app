package tv.anime.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import kotlinx.coroutines.launch
import tv.anime.app.data.StartupImagePreloader
import tv.anime.app.data.tmdb.HeroBackdropRepository
import tv.anime.app.data.tmdb.TitleLogoRepository
import tv.anime.app.domain.AnimeHero

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HeroBannerSlider(
    items: List<AnimeHero>,
    onOpenDetails: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenMenu: () -> Unit,
    heroRequester: FocusRequester,
    homeListState: LazyListState,
    rememberLastContentFocus: (FocusRequester) -> Unit,
    onHeroFocusChanged: (Boolean) -> Unit,
    heroHeight: Dp,
    onMoveDown: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Auto-advance; pauses while hero area is focused to avoid selection drifting
    var isHeroFocused by remember { mutableStateOf(false) }

    // How long each hero slide stays before advancing.
    val slideDurationMs = 14_000L

    // Progress for the CTA bar (0f..1f). Resets each page.
    val slideProgress = remember { Animatable(0f) }

    LaunchedEffect(items.size) {
        // Keep the index in-range if the list updates.
        if (items.isEmpty()) {
            currentIndex = 0
        } else if (currentIndex !in items.indices) {
            currentIndex = 0
        }
    }

    LaunchedEffect(items.size, currentIndex) {
        if (items.size <= 1) return@LaunchedEffect

        // Restart progress for this page.
        slideProgress.snapTo(0f)

        // Animate progress; when it completes, advance page.
        slideProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = slideDurationMs.toInt(),
                easing = LinearEasing
            )
        )

        if (items.isNotEmpty()) currentIndex = (currentIndex + 1) % items.size
    }

    val context = LocalContext.current
    val density = LocalDensity.current

    val leftGradient = remember {
        Brush.horizontalGradient(
            0.0f to Color.Black.copy(alpha = 0.65f),
            0.35f to Color.Black.copy(alpha = 0.10f),
            1.0f to Color.Transparent
        )
    }
    val bottomGradient = remember {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.60f to Color.Black.copy(alpha = 0.20f),
            1.0f to Color.Black
        )
    }
    val overallDarken = remember {
        Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = 0.05f),
            0.55f to Color.Black.copy(alpha = 0.55f),
            1.0f to Color.Black.copy(alpha = 0.90f)
        )
    }

    // Render the content from the current slide, but keep the focus target stable.
    val currentHero by remember(items, currentIndex) {
        derivedStateOf { items.getOrNull(currentIndex) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .focusRequester(heroRequester)
            .onFocusChanged {
                isHeroFocused = it.isFocused
                onHeroFocusChanged(it.isFocused)
                if (it.isFocused) {
                    rememberLastContentFocus(heroRequester)

                    // Correct any post-focus top drift across a few frames.
                    scope.launch {
                        repeat(30) {
                            withFrameNanos { }

                            if (
                                !homeListState.isScrollInProgress &&
                                homeListState.firstVisibleItemIndex == 0 &&
                                homeListState.firstVisibleItemScrollOffset != 0
                            ) {
                                homeListState.animateScrollToItem(0, 0)
                            } else if (
                                homeListState.firstVisibleItemIndex == 0 &&
                                homeListState.firstVisibleItemScrollOffset == 0
                            ) {
                                return@launch
                            }
                        }
                    }
                }
            }
            .focusable()
            .onPreviewKeyEvent { e ->
                val isActivateKey = when (e.key) {
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter,
                    Key.Spacebar,
                    Key.ButtonA -> true
                    else -> false
                }

                val hero = currentHero
                if (isActivateKey && hero != null) {
                    return@onPreviewKeyEvent when (e.type) {
                        KeyEventType.KeyDown -> {
                            onOpenDetails(hero.id)
                            true
                        }
                        KeyEventType.KeyUp -> true
                        else -> true
                    }
                }

                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Back -> {
                        onOpenMenu(); true
                    }
                    Key.DirectionDown -> {
                        onMoveDown(); true
                    }
                    Key.DirectionLeft -> {
                        if (items.size > 1) {
                            val prev = (currentIndex - 1 + items.size) % items.size
                            currentIndex = prev
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (items.size > 1) {
                            val next = (currentIndex + 1) % items.size
                            currentIndex = next
                            true
                        } else false
                    }
                    Key.Menu -> {
                        onRefresh(); true
                    }
                    else -> false
                }
            }
    ) {
        val pageWidthPx = with(density) { maxWidth.roundToPx().toFloat() }
        val heroHPx = with(density) { heroHeight.roundToPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(0.dp))
        ) {

            // Prefetch artwork for current/adjacent slides (metadata + decoded bitmaps).
            // This is critical on Android TV: decoding during a crossfade looks like pop-in.
            LaunchedEffect(currentIndex, items.size, pageWidthPx, heroHPx) {
                if (items.isEmpty()) return@LaunchedEffect

                val prev = (currentIndex - 1 + items.size) % items.size
                val next = (currentIndex + 1) % items.size
                val next2 = (currentIndex + 2) % items.size

                // 1) Metadata prefetch (TMDB lookups).
                val around = listOfNotNull(
                    items.getOrNull(currentIndex),
                    items.getOrNull(prev),
                    items.getOrNull(next),
                    items.getOrNull(next2)
                )
                TitleLogoRepository.prefetch(*around.toTypedArray())
                HeroBackdropRepository.prefetch(*around.toTypedArray())

                // 2) Decode warmup into Coil cache for the same sizes we render.
                val sizes = StartupImagePreloader.Sizes(
                    heroWidthPx = pageWidthPx.toInt().coerceAtLeast(1),
                    heroHeightPx = heroHPx.coerceAtLeast(1),
                    // Posters aren't part of hero navigation; keep placeholders.
                    posterWidthPx = 1,
                    posterHeightPx = 1
                )
                runCatching {
                    StartupImagePreloader.preloadHeroNeighborhood(
                        context = context,
                        heroes = items,
                        indices = listOf(currentIndex, prev, next, next2),
                        sizes = sizes
                    )
                }
            }

            // Render a single hero image and crossfade between slides.
            Crossfade(
                targetState = currentHero,
                animationSpec = tween(durationMillis = 520),
                label = "hero_crossfade"
            ) { hero ->
                if (hero == null) return@Crossfade

                val fallbackUrl = hero.bannerUrl ?: hero.coverUrl
                val cachedBackdrop = remember(hero.id) { HeroBackdropRepository.peekBackdropUrl(hero.id) }
                var displayUrl by remember(hero.id) { mutableStateOf(cachedBackdrop ?: fallbackUrl) }

                LaunchedEffect(hero.id) {
                    val tmdbUrl = HeroBackdropRepository.getBackdropUrl(hero)
                    val resolved = tmdbUrl ?: fallbackUrl
                    if (!resolved.isNullOrBlank() && resolved != displayUrl) {
                        displayUrl = resolved
                    }
                }

                val heroRequest = remember(displayUrl, pageWidthPx, heroHPx) {
                    ImageRequest.Builder(context)
                        .data(displayUrl)
                        .size(pageWidthPx.toInt(), heroHPx)
                        .precision(Precision.EXACT)
                        .crossfade(false) // Crossfade is handled by Compose, not Coil.
                        .allowHardware(AllowHardwareBitmaps)
                        .build()
                }

                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = heroRequest,
                        contentDescription = hero.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = {
                            // If the high-quality TMDB backdrop fails to load, fall back to AniList banner/cover.
                            if (fallbackUrl != null && displayUrl != fallbackUrl) {
                                displayUrl = fallbackUrl
                            }
                        }
                    )
                    Box(Modifier.fillMaxSize().background(leftGradient))
                    Box(Modifier.fillMaxSize().background(bottomGradient))
                    Box(Modifier.fillMaxSize().background(overallDarken))
                }
            }

            // Overlay the metadata + a single stable focus target (Watch Now) based on current page.
            val hero = currentHero
            if (hero != null) {
                var logoUrl by remember(hero.id) {
                    mutableStateOf(TitleLogoRepository.peekLogoUrl(hero.id))
                }
                LaunchedEffect(hero.id) {
                    logoUrl = TitleLogoRepository.getLogoUrl(hero)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = HomeContentInsetStart,
                            end = HomePaddingH,
                            top = 22.dp,
                            bottom = 22.dp
                        ),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (!logoUrl.isNullOrBlank()) {
                        val logoReq = remember(logoUrl) {
                            ImageRequest.Builder(context)
                                .data(logoUrl)
                                .size(520, 220)
                                .precision(Precision.EXACT)
                                .crossfade(false)
                                .allowHardware(AllowHardwareBitmaps)
                                .build()
                        }
                        AsyncImage(
                            model = logoReq,
                            contentDescription = null,
                            modifier = Modifier
                                .height(92.dp)
                                .fillMaxWidth(0.50f),
                            contentScale = ContentScale.Fit,
                            onError = {
                                // If the logo fails, fall back to rendering the title text.
                                logoUrl = null
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    } else {
                        Text(
                            text = hero.title,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Text(
                        text = buildHeroMeta(hero),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (hero.genres.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = hero.genres.take(6).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val desc = hero.description
                        ?.let { normalizeDescriptionText(it) }
                        ?.takeIf { it.isNotBlank() }

                    if (desc != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // CTA is visual only; activation is handled by the hero focus target.
                    WatchNowProgressPill(
                        onClick = { onOpenDetails(hero.id) },
                        showProgress = isHeroFocused && items.size > 1,
                        progress = slideProgress.value
                    )
                }
            }

            // Slide dots: visual indicator only (never focusable), always white.
            if (items.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(items.size) { i ->
                        val selected = currentIndex == i
                        val a by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.35f,
                            animationSpec = tween(durationMillis = 220),
                            label = "dot_alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(if (selected) 10.dp else 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = a))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchNowProgressPill(
    onClick: () -> Unit,
    showProgress: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val pill = RoundedCornerShape(percent = 50)
    val p = if (showProgress) progress.coerceIn(0f, 1f) else 0f

    val baseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val fillColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onPrimary

    // Non-focusable visual CTA (hero owns focus), but still clickable.
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .wrapContentWidth()
            .defaultMinSize(minWidth = 180.dp)
            .height(38.dp)
            .clip(pill)
            .background(baseColor)
            .drawWithContent {
                if (p > 0f) {
                    val w = size.width * p
                    drawRoundRect(
                        color = fillColor,
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
                drawContent()
            }
            .clickable(
                interactionSource = interaction,
                indication = null, // keep it clean; TV focus is on the hero
                onClick = onClick
            )
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Watch Now",
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun buildHeroMeta(hero: AnimeHero): String {
    val seasonPretty = hero.season
        ?.lowercase()
        ?.replaceFirstChar { it.uppercase() }

    val seasonLabel = listOfNotNull(seasonPretty, hero.seasonYear?.toString())
        .joinToString(" ")
        .ifBlank { null }
        ?.let { "Season: $it" }

    val episodesLabel = hero.episodes?.let { "Episodes: $it" } ?: "Episodes: Ongoing"
    val ratingLabel = hero.averageScore?.let { "Rating: $it%" }

    return listOfNotNull(seasonLabel, episodesLabel, ratingLabel).joinToString("  •  ")
}
