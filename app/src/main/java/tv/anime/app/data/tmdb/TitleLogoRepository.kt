package tv.anime.app.data.tmdb

import tv.anime.app.BuildConfig
import tv.anime.app.domain.AnimeDetails
import tv.anime.app.domain.AnimeHero
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a transparent title-logo art URL for a hero item.
 *
 * Implementation details:
 * - Uses TMDB v3 search to find a matching TV series by title (optionally year).
 * - Queries /tv/{id}/images and picks the best logo.
 * - Caches results in-memory by AniList id to avoid repeated network calls.
 */
object TitleLogoRepository {

    // ConcurrentHashMap does NOT permit null keys or null values.
    // Use a sentinel value to cache "no logo" results.
    private const val NO_LOGO = "__NO_LOGO__"

    private val cache = ConcurrentHashMap<Int, String>()

    /** Returns the cached logo URL immediately (no network), or null if not cached/available. */
    fun peekLogoUrl(anilistId: Int): String? {
        val cached = cache[anilistId]
        return cached?.takeIf { it != NO_LOGO }
    }

    private val client: TmdbClient? by lazy {
        val key = BuildConfig.TMDB_API_KEY.trim()
        if (key.isBlank()) null else TmdbClient(apiKey = key)
    }

    suspend fun getLogoUrl(hero: AnimeHero): String? =
        getLogoUrlInternal(
            anilistId = hero.id,
            title = hero.title,
            seasonYear = hero.seasonYear
        )

    /**
     * Details pages often use season-specific display titles. This method uses the same
     * normalization + fallback matching as the hero, but without requiring seasonYear.
     */
    suspend fun getLogoUrl(details: AnimeDetails): String? =
        getLogoUrlInternal(
            anilistId = details.id,
            title = details.title,
            seasonYear = null
        )

    private suspend fun getLogoUrlInternal(
        anilistId: Int,
        title: String,
        seasonYear: Int?
    ): String? {
        val cached = cache[anilistId]
        if (cached != null) return cached.takeIf { it != NO_LOGO }

        val tmdb = client ?: run {
            cache[anilistId] = NO_LOGO
            return null
        }

        val query = title.trim()
        if (query.isBlank()) {
            cache[anilistId] = NO_LOGO
            return null
        }

        val queries = buildCandidateQueries(query)
        val matchResult = findFirstMatch(tmdb, queries, seasonYear)
        val match = matchResult.item

        // Only cache a negative result if TMDB was reachable and still returned no match.
        // If a transient error occurred, don't permanently cache "no logo".
        if (match == null) {
            if (!matchResult.hadError) {
                cache[anilistId] = NO_LOGO
            }
            return null
        }

        val images = try {
            tmdb.tvImages(match.id)
        } catch (_: Throwable) {
            // Transient failure: don't cache a negative.
            return null
        }
        val bestLogo = images.logos
            .asSequence()
            // Prefer English or no-language logos.
            .sortedWith(
                compareByDescending<TmdbImage> { it.language == "en" }
                    .thenByDescending { it.voteAverage }
                    .thenByDescending { it.voteCount }
                    .thenByDescending { it.width }
            )
            .firstOrNull()

        val url = bestLogo?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" }

        // At this point we have a definite TMDB response; cache either the URL or "no logo".
        cache[anilistId] = url ?: NO_LOGO
        return url
    }

    /** Optional: allow prefetching current/adjacent items to hide latency. */
    suspend fun prefetch(vararg heroes: AnimeHero) {
        for (h in heroes) {
            if (!cache.containsKey(h.id)) {
                runCatching { getLogoUrl(h) }
            }
        }
    }
}

