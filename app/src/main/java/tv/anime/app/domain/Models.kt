package tv.anime.app.domain

data class AnimeCard(
    val id: Int,
    val title: String,
    val coverUrl: String?
)

/**
 * Rich metadata for the hero banner.
 *
 * Note: "seasons" in the UI is represented as the single AniList season + year,
 * since AniList does not provide a stable "number of seasons" field.
 */
data class AnimeHero(
    val id: Int,
    val title: String,
    val bannerUrl: String?,
    val coverUrl: String?,
    val episodes: Int?,
    val season: String?,
    val seasonYear: Int?,
    val averageScore: Int?,
    val genres: List<String>,
    val description: String?
)

data class AnimeDetails(
    val id: Int,
    val title: String,
    val description: String?,
    val bannerUrl: String?,
    val coverUrl: String?,
    val genres: List<String>,
    val averageScore: Int?,
    // Extended AniList fields for richer Details UI.
    // Default values keep call-sites source-compatible.
    val episodes: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null
)

data class Episode(
    val number: Int,
    val title: String? = null,
    val sourceId: String = number.toString()
)

data class StreamInfo(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

interface EpisodeProvider {

    /** Default: treat the current AniList media as the only season. */
    suspend fun getSeasons(rootMediaId: Int): List<Season> =
        listOf(Season(mediaId = rootMediaId, label = "Season 1"))

    suspend fun getEpisodes(anilistMediaId: Int): List<Episode>

    suspend fun resolveStream(anilistMediaId: Int, episode: Episode): StreamInfo
}
