package tv.anime.app.data.provider

import tv.anime.app.domain.*

/**
 * Placeholder episode provider.
 *
 * Replace this with a licensed catalog integration later.
 * The default implementation returns a public test stream so you can validate end-to-end playback.
 */
class DemoEpisodeProvider : EpisodeProvider {

    override suspend fun getEpisodes(anilistMediaId: Int): List<Episode> {
        // Placeholder: pretend every show has 12 episodes
        return (1..12).map { Episode(number = it, title = "Episode $it") }
    }

    override suspend fun resolveStream(anilistMediaId: Int, episode: Episode): StreamInfo {
        // Public test HLS stream (replace with your licensed backend later)
        val url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        return StreamInfo(url = url)
    }
}
