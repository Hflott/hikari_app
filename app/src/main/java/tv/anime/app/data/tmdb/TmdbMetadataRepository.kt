package tv.anime.app.data.tmdb

import tv.anime.app.BuildConfig
import tv.anime.app.domain.AnimeDetails
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Best-effort metadata fallback when AniList is temporarily unavailable.
 *
 * Strategy:
 * - Match the title against TMDB Search (TV first, then Movie).
 * - Fetch rich metadata from /tv/{id} or /movie/{id}.
 * - Cache results by AniList id to avoid repeated network calls.
 */
object TmdbMetadataRepository {

    private val detailsCache = ConcurrentHashMap<Int, AnimeDetails>()
    private val missCache = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    private val client: TmdbClient? by lazy {
        val key = BuildConfig.TMDB_API_KEY.trim()
        if (key.isBlank()) null else TmdbClient(apiKey = key)
    }

    /** Returns a cached details object immediately, or null if not cached. */
    fun peekDetails(anilistId: Int): AnimeDetails? = detailsCache[anilistId]

    suspend fun getDetails(
        anilistId: Int,
        title: String,
        seasonYear: Int? = null
    ): AnimeDetails? {
        detailsCache[anilistId]?.let { return it }
        if (missCache.contains(anilistId)) return null

        val tmdb = client ?: return null

        val q = title.trim()
        if (q.isBlank()) return null

        val queries = buildCandidateQueries(q)

        // 1) Prefer TV matches.
        val tvMatch = findFirstMatch(tmdb, queries, year = seasonYear)
        if (tvMatch.item != null) {
            val tv = runCatching { tmdb.tvDetails(tvMatch.item.id) }.getOrNull()
                ?: return null

            val details = tv.toAnimeDetails(
                anilistId = anilistId,
                fallbackTitle = q
            )
            detailsCache[anilistId] = details
            return details
        }

        // 2) If no TV match, try Movie.
        val movieMatch = findFirstMovieMatch(tmdb, queries, year = seasonYear)
        if (movieMatch.item != null) {
            val m = runCatching { tmdb.movieDetails(movieMatch.item.id) }.getOrNull()
                ?: return null

            val details = m.toAnimeDetails(
                anilistId = anilistId,
                fallbackTitle = q
            )
            detailsCache[anilistId] = details
            return details
        }

        // Only cache a definitive miss when TMDB was reachable and still returned nothing.
        if (!tvMatch.hadError && !movieMatch.hadError) {
            missCache.add(anilistId)
        }
        return null
    }

    private data class MovieMatchResult(
        val item: TmdbMovieSearchItem?,
        val hadError: Boolean
    )

    private suspend fun findFirstMovieMatch(
        tmdb: TmdbClient,
        queries: List<String>,
        year: Int?
    ): MovieMatchResult {
        var hadError = false

        for (q in queries) {
            val withYear = try {
                tmdb.searchMovie(q, primaryReleaseYear = year)
            } catch (_: Throwable) {
                hadError = true
                null
            }
            val m1 = withYear?.results?.firstOrNull()
            if (m1 != null) return MovieMatchResult(m1, hadError)

            val noYear = try {
                tmdb.searchMovie(q, primaryReleaseYear = null)
            } catch (_: Throwable) {
                hadError = true
                null
            }
            val m2 = noYear?.results?.firstOrNull()
            if (m2 != null) return MovieMatchResult(m2, hadError)
        }

        return MovieMatchResult(null, hadError)
    }

    private fun TmdbTvDetailsResponse.toAnimeDetails(anilistId: Int, fallbackTitle: String): AnimeDetails {
        val year = firstAirDate?.take(4)?.toIntOrNull()
        val score = voteAverage?.let { (it * 10.0).roundToInt().coerceIn(0, 100) }

        return AnimeDetails(
            id = anilistId,
            title = name?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            description = overview,
            bannerUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" },
            coverUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            genres = genres.map { it.name },
            averageScore = score,
            episodes = numberOfEpisodes,
            format = "TV",
            status = status,
            season = null,
            seasonYear = year
        )
    }

    private fun TmdbMovieDetailsResponse.toAnimeDetails(anilistId: Int, fallbackTitle: String): AnimeDetails {
        val year = releaseDate?.take(4)?.toIntOrNull()
        val score = voteAverage?.let { (it * 10.0).roundToInt().coerceIn(0, 100) }

        return AnimeDetails(
            id = anilistId,
            title = title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            description = overview,
            bannerUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" },
            coverUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            genres = genres.map { it.name },
            averageScore = score,
            episodes = null,
            format = "MOVIE",
            status = status,
            season = null,
            seasonYear = year
        )
    }
}
