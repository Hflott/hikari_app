package tv.anime.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision

/**
 * Branded startup screen shown while the app preloads the first batch of content.
 *
 * - Animated "Yuki" wordmark.
 * - Lightweight hidden image warm-up to reduce visible pop-in once the UI appears.
 */
@Composable
fun StartupLoadingScreen(
    brand: String = "Yuki",
    warmupUrls: List<String> = emptyList()
) {
    val infinite = rememberInfiniteTransition(label = "startup")
    val pulse = infinite.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glow = infinite.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val dot = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )

    val background = MaterialTheme.colorScheme.background
    val onBackground = MaterialTheme.colorScheme.onBackground

    // Warm up a small set of images (banner/posters/logos) while the splash is visible.
    // This is intentionally capped to keep composition light.
    HiddenImageWarmup(urls = warmupUrls)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = brand,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    }
                    .alpha(glow.value),
                color = Color.Unspecified,
                // Compose Text supports a brush via TextStyle in newer versions, but to stay
                // compatible we simply draw with the default color and add a "glow" pulse.
            )

            // Accent bar under the logo.
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(onBackground.copy(alpha = 0.35f))
            )
            Spacer(Modifier.height(18.dp))

            // Simple "loading dots" to show activity.
            val dots = when {
                dot.value < 0.33f -> "."
                dot.value < 0.66f -> ".."
                else -> "..."
            }
            Text(
                text = "Loading$dots",
                style = MaterialTheme.typography.titleMedium,
                color = onBackground.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun HiddenImageWarmup(urls: List<String>) {
    if (urls.isEmpty()) return
    val context = LocalContext.current

    // Keep it small: hero banners + a handful of posters is plenty.
    val warm = remember(urls) { urls.filter { it.isNotBlank() }.distinct().take(64) }

    // Off-screen (alpha=0 + tiny size), but still triggers Coil fetch/caching.
    Row(modifier = Modifier.alpha(0f)) {
        warm.forEach { url ->
            val req = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .precision(Precision.INEXACT)
                    .crossfade(false)
                    .allowHardware(AllowHardwareBitmaps)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = null,
                modifier = Modifier.size(1.dp)
            )
        }
    }
}
