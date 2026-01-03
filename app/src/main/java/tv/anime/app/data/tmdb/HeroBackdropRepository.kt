package tv.anime.app.data.tmdb

import tv.anime.app.BuildConfig
import tv.anime.app.domain.AnimeDetails
import tv.anime.app.domain.AnimeHero
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a high-quality 16:9 backdrop for use in the home hero slider.
 *
 * The AniList banner is often too low-resolution for a TV hero. This repository
 * attempts to match the title against TMDB TV series search and uses the
 * series backdrop (w1280) when available, falling back to the AniList banner
 * or cover in the UI.
 */
object HeroBackdropRepository {

    private const val NO_BACKDROP = "__NO_BACKDROP__"

    // Cache by AniList id.
    private val cache = ConcurrentHashMap<Int, String>()

    /** Returns the cached backdrop URL immediately (no network), or null if not cached/available. */
    fun peekBackdropUrl(anilistId: Int): String? {
        val cached = cache[anilistId]
        return cached?.takeIf { it != NO_BACKDROP }
    }

    private val client: TmdbClient? by lazy {
        val key = BuildConfig.TMDB_API_KEY.trim()
        if (key.isBlank()) null else TmdbClient(apiKey = key)
    }

    /** Returns a TMDB backdrop URL if available; otherwise null. */
    suspend fun getBackdropUrl(hero: AnimeHero): String? =
        getBackdropUrlInternal(
            anilistId = hero.id,
            title = hero.title,
            seasonYear = hero.seasonYear
        )

    /** Details pages may not have a season year; match without year. */
    suspend fun getBackdropUrl(details: AnimeDetails): String? =
        getBackdropUrlInternal(
            anilistId = details.id,
            title = details.title,
            seasonYear = null
        )

    private suspend fun getBackdropUrlInternal(
        anilistId: Int,
        title: String,
        seasonYear: Int?
    ): String? {
        val cached = cache[anilistId]
        if (cached != null) return cached.takeIf { it != NO_BACKDROP }

        val tmdb = client ?: run {
            cache[anilistId] = NO_BACKDROP
            return null
        }

        val query = title.trim()
        if (query.isBlank()) {
            cache[anilistId] = NO_BACKDROP
            return null
        }

        val queries = buildCandidateQueries(query)
        val matchResult = findFirstMatch(tmdb, queries, seasonYear)
        val match = matchResult.item

        // Only cache a negative result if TMDB was reachable and still returned no match.
        // If a transient error occurred, don't permanently cache "no backdrop".
        if (match == null) {
            if (!matchResult.hadError) {
                cache[anilistId] = NO_BACKDROP
            }
            return null
        }

        // /search/tv returns backdrop_path, which is usually sufficient.
        val url = match.backdropPath?.let { path ->
            // For TV heroes and details headers, use the highest quality.
            // You can switch this back to "w1280" if bandwidth is a concern.
            "https://image.tmdb.org/t/p/original$path"
        }

        cache[anilistId] = url ?: NO_BACKDROP
        return url
    }

    /** Optional: allow prefetching current/adjacent items to hide latency. */
    suspend fun prefetch(vararg heroes: AnimeHero) {
        for (h in heroes) {
            if (!cache.containsKey(h.id)) {
                runCatching { getBackdropUrl(h) }
            }
        }
    }
}
