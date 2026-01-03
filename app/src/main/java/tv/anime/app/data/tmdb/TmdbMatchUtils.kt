package tv.anime.app.data.tmdb

/**
 * Utility helpers for matching AniList/Display titles to TMDB TV series.
 *
 * Many anime are labeled with season/cour/part suffixes in the UI (e.g.,
 * "My Hero Academia FINAL SEASON"), while TMDB generally stores the base
 * series title. These helpers generate candidate queries and try them
 * against TMDB search with/without a year filter.
 */

internal fun buildCandidateQueries(raw: String): List<String> {
    val t = raw.trim()
    if (t.isBlank()) return emptyList()

    val stripSuffix = Regex(
        pattern = "(?i)\\s*(?:\\(|\\[)?\\s*(final\\s*season|the\\s*final\\s*season|season\\s*\\d+|s\\d+|part\\s*\\d+|cour\\s*\\d+)\\s*(?:\\)|\\])?\\s*$"
    )

    fun normalize(s: String): String =
        s.replace('×', 'x')
            .replace(Regex("\\s+"), " ")
            .trim()

    val noSuffix = normalize(t.replace(stripSuffix, ""))
    val beforeColon = normalize(t.substringBefore(":"))
    val beforeDash = normalize(
        t.substringBefore(" - ")
            .substringBefore(" – ")
            .substringBefore("-")
            .substringBefore("–")
    )

    return listOf(
        normalize(t),
        noSuffix,
        beforeColon,
        beforeDash
    )
        .map { it.trim() }
        .filter { it.length >= 3 }
        .distinct()
}

/**
 * Match helper that distinguishes between "no results" and transient failures.
 *
 * This is important because callers cache "no asset" results. If a network/TMDB
 * error occurs, we should NOT cache that negative result permanently.
 */
internal data class TmdbMatchResult(
    val item: TmdbSearchItem?,
    val hadError: Boolean
)

internal suspend fun findFirstMatch(
    tmdb: TmdbClient,
    queries: List<String>,
    year: Int?
): TmdbMatchResult {
    var hadError = false

    for (q in queries) {
        // Try with year first (when present), then without year as fallback.
        val withYear = try {
            tmdb.searchTv(q, firstAirDateYear = year)
        } catch (_: Throwable) {
            hadError = true
            null
        }
        val m1 = withYear?.results?.firstOrNull()
        if (m1 != null) return TmdbMatchResult(m1, hadError)

        val noYear = try {
            tmdb.searchTv(q, firstAirDateYear = null)
        } catch (_: Throwable) {
            hadError = true
            null
        }
        val m2 = noYear?.results?.firstOrNull()
        if (m2 != null) return TmdbMatchResult(m2, hadError)
    }

    return TmdbMatchResult(null, hadError)
}
