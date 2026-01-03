package tv.anime.app.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import tv.anime.app.domain.Episode
import tv.anime.app.domain.EpisodeProvider
import tv.anime.app.net.SharedOkHttp

@OptIn(UnstableApi::class)
@UiComposable
@Composable
fun PlayerScreen(
    episodeProvider: EpisodeProvider,
    anilistId: Int,
    episodeNumber: Int,
    onExit: () -> Unit
) {
    BackHandler(onBack = onExit)

    // Force a true fullscreen playback surface (no status/navigation bars).
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? androidx.activity.ComponentActivity)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var streamUrl by remember { mutableStateOf<String?>(null) }
    var streamHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(anilistId, episodeNumber) {
        streamUrl = null
        streamHeaders = emptyMap()
        error = null

        runCatching {
            val ep = Episode(number = episodeNumber)
            val stream = episodeProvider.resolveStream(anilistId, ep)
            streamUrl = stream.url
            streamHeaders = stream.headers
        }.onFailure {
            error = it.message ?: "Unknown error"
        }
    }

    val url = streamUrl
    if (error != null) {
        Box(Modifier.fillMaxSize()) { Text("Playback error: $error") }
        return
    }
    if (url == null) {
        Box(Modifier.fillMaxSize()) { Text("Resolving stream…") }
        return
    }

    val context = LocalContext.current
    val player = remember(url) { ExoPlayer.Builder(context).build() }

    LaunchedEffect(player, url, streamHeaders) {
        val httpFactory = OkHttpDataSource.Factory(SharedOkHttp.client)
            .setDefaultRequestProperties(streamHeaders)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
        player.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(url)))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Always fullscreen: no controller chrome.
                useController = false
            }
        },
        update = { it.player = player }
    )
}
