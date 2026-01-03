package tv.anime.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import tv.anime.app.domain.AnimeCard

/** Shared poster tile dimensions (matches the Home feed). */
internal val PosterTileWidth: Dp = 130.dp
internal val PosterTileHeight: Dp = 195.dp
internal val PosterTileTitleSpacer: Dp = 8.dp
internal val PosterTileTitleHeight: Dp = 20.dp

/**
 * Shared poster tile used across the app (Home, Search, etc.).
 *
 * - Fixed dimensions to match the Home feed.
 * - Exact Coil sizing to reduce over-fetching and improve scrolling performance.
 * - Optional focus tracking hook to support "remember last focused content" behavior.
 */
@Composable
fun PosterTile(
    anime: AnimeCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    rememberLastContentFocus: ((FocusRequester) -> Unit)? = null,
    onFocused: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val localRequester = remember { FocusRequester() }
    val requester = focusRequester ?: localRequester

    val (wPx, hPx) = remember(density) {
        with(density) { PosterTileWidth.roundToPx() to PosterTileHeight.roundToPx() }
    }

    val request = remember(anime.coverUrl, wPx, hPx) {
        ImageRequest.Builder(context)
            .data(anime.coverUrl)
            .size(wPx, hPx)
            .precision(Precision.EXACT)
            .crossfade(false)
            .allowHardware(AllowHardwareBitmaps)
            .build()
    }

    Column(
        modifier = Modifier
            .width(PosterTileWidth)
            .height(PosterTileHeight + PosterTileTitleSpacer + PosterTileTitleHeight)
    ) {
        Card(
            onClick = {
                onFocused?.invoke()
                onClick()
            },
            modifier = modifier
                .size(width = PosterTileWidth, height = PosterTileHeight)
                .focusRequester(requester)
                .onFocusChanged {
                    if (it.isFocused) {
                        onFocused?.invoke()
                        rememberLastContentFocus?.invoke(requester)
                    }
                }
                .onPreviewKeyEvent { e ->
                    // Keep "last focus" up-to-date on directional navigation as well.
                    if (
                        e.type == KeyEventType.KeyDown &&
                        (e.key == Key.DirectionLeft ||
                            e.key == Key.DirectionRight ||
                            e.key == Key.DirectionUp ||
                            e.key == Key.DirectionDown)
                    ) {
                        rememberLastContentFocus?.invoke(requester)
                    }
                    false
                },
            scale = CardDefaults.scale(focusedScale = 1.08f, pressedScale = 0.995f)
        ) {
            AsyncImage(
                model = request,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.height(PosterTileTitleSpacer))

        Text(
            text = anime.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(PosterTileTitleHeight)
        )
    }
}

/**
 * Shared loading skeleton for poster tiles.
 *
 * Not focusable; intended for placeholders while paging/app loads.
 */
@Composable
fun PosterTileSkeleton(
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    val shape = RoundedCornerShape(10.dp)

    // Simple pulse (reliable and cheap on TV devices; avoids shader-heavy shimmer).
    val t = rememberInfiniteTransition(label = "poster_skeleton")
    val a = t.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "poster_skeleton_alpha"
    )

    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = a.value)
    val line = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = (a.value * 0.85f).coerceIn(0f, 1f))

    Column(
        modifier = Modifier
            .width(PosterTileWidth)
            .height(PosterTileHeight + PosterTileTitleSpacer + PosterTileTitleHeight)
            .then(modifier)
            .focusProperties { canFocus = false }
            .clickable(enabled = false) {}
    ) {
        Box(
            modifier = Modifier
                .size(width = PosterTileWidth, height = PosterTileHeight)
                .clip(shape)
                .background(base)
        )

        if (showTitle) {
            Spacer(Modifier.height(PosterTileTitleSpacer))
            Box(
                modifier = Modifier
                    .height(PosterTileTitleHeight)
                    .width(PosterTileWidth * 0.85f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(line)
            )
        }
    }
}
