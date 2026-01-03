package tv.anime.app.data

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tv.anime.app.data.anilist.AniListClient
import tv.anime.app.data.anilist.Queries
import tv.anime.app.data.tmdb.TmdbMetadataRepository
import tv.anime.app.domain.AnimeCard
import tv.anime.app.domain.AnimeDetails
import tv.anime.app.domain.AnimeHero
import java.util.concurrent.ConcurrentHashMap

class AnimeRepository(private val client: AniListClient) {

    private data class BasicInfo(
        val title: String,
        val seasonYear: Int? = null
    )

    /**
     * Lightweight metadata cache used for TMDB fallback.
     *
     * Rationale: when AniList is temporarily unavailable (or rate-limited),
     * the Details screen can still resolve a title against TMDB using
     * information we already displayed in lists.
     */
    private val basicInfoCache = ConcurrentHashMap<Int, BasicInfo>()

    private fun rememberBasicInfo(anilistId: Int, title: String, seasonYear: Int? = null) {
        // Only overwrite if we have strictly more information (year) than the prior entry.
        val existing = basicInfoCache[anilistId]
        if (existing == null || (existing.seasonYear == null && seasonYear != null)) {
            basicInfoCache[anilistId] = BasicInfo(title = title, seasonYear = seasonYear)
        }
    }

    data class PageResult<T>(
        val items: List<T>,
        val currentPage: Int,
        val hasNextPage: Boolean
    )

    /**
     * "Recent" means: anime that had a new episode air recently and is currently RELEASING.
     *
     * AniList's AiringSchedule is episode-based, so we pull a larger page of schedule entries
     * for a time window and then de-duplicate by media id.
     */
    suspend fun recent(page: Int = 1, perPage: Int = 50): List<AnimeCard> {
        // Window: last 7 days (unix seconds).
        val now = (System.currentTimeMillis() / 1000L).toInt()
        val from = now - 7 * 24 * 60 * 60

        val vars = AniListClient.variablesOf(
            mapOf(
                "page" to page,
                "perPage" to perPage,
                "airingAtGreater" to from,
                "airingAtLesser" to now
            )
        )

        val resp = client.post(Queries.RECENT, vars)

        val schedules = resp.data
            ?.get("Page")?.jsonObject
            ?.get("airingSchedules")?.jsonArray
            ?: return emptyList()

        // Preserve order (most recent first), remove duplicates by media id.
        val byId = LinkedHashMap<Int, AnimeCard>()

        for (s in schedules) {
            val scheduleObj = s.jsonObject
            val mediaObj = scheduleObj["media"]?.jsonObject ?: continue

            // Filter adult content (e.g., hentai).
            val isAdult = mediaObj["isAdult"]?.jsonPrimitive?.contentOrNull == "true"
            if (isAdult) continue

            // Only show currently airing shows.
            val status = mediaObj["status"]?.jsonPrimitive?.contentOrNull
            if (status != "RELEASING") continue

            val id = mediaObj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: continue
            if (byId.containsKey(id)) continue

            val titleObj = mediaObj["title"]?.jsonObject
            val title = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
                ?: "Unknown"

            val cover = mediaObj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull
            rememberBasicInfo(id, title)
            byId[id] = AnimeCard(id, title, cover)
        }

        return byId.values.take(20)
    }

    suspend fun trending(page: Int = 1, perPage: Int = 20): List<AnimeCard> {
        val vars = AniListClient.variablesOf(mapOf("page" to page, "perPage" to perPage))
        val resp = client.post(Queries.TRENDING, vars)

        val media = resp.data
            ?.get("Page")?.jsonObject
            ?.get("media")?.jsonArray
            ?: return emptyList()

        return media.mapNotNull { m ->
            val obj = m.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: return@mapNotNull null
            val titleObj = obj["title"]?.jsonObject
            val title = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
                ?: "Unknown"
            val cover = obj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull
            rememberBasicInfo(id, title)
            AnimeCard(id, title, cover)
        }
    }

    /**
     * Trending titles for the hero slider.
     *
     * We only return entries that have a bannerImage, since the UI is optimized
     * for wide hero artwork ("good quality big banners").
     */
    suspend fun heroTrending(page: Int = 1, perPage: Int = 30, take: Int = 10): List<AnimeHero> {
        val vars = AniListClient.variablesOf(mapOf("page" to page, "perPage" to perPage))
        val resp = client.post(Queries.HERO_TRENDING, vars)

        val media = resp.data
            ?.get("Page")?.jsonObject
            ?.get("media")?.jsonArray
            ?: return emptyList()

        val heroes = media.mapNotNull { m ->
            val obj = m.jsonObject

            val status = obj["status"]?.jsonPrimitive?.contentOrNull
            if (status == "NOT_YET_RELEASED") return@mapNotNull null

            val banner = obj["bannerImage"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: return@mapNotNull null

            val titleObj = obj["title"]?.jsonObject
            val title = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
                ?: "Unknown"

            val cover = obj["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.contentOrNull

            val genres = obj["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

            val seasonYear = obj["seasonYear"]?.jsonPrimitive?.intOrNull
            rememberBasicInfo(id, title, seasonYear)

            AnimeHero(
                id = id,
                title = title,
                bannerUrl = banner,
                coverUrl = cover,
                episodes = obj["episodes"]?.jsonPrimitive?.intOrNull,
                season = obj["season"]?.jsonPrimitive?.contentOrNull,
                seasonYear = seasonYear,
                averageScore = obj["averageScore"]?.jsonPrimitive?.intOrNull,
                genres = genres,
                description = obj["description"]?.jsonPrimitive?.contentOrNull
            )
        }

        return heroes.take(take)
    }

    suspend fun popular(page: Int = 1, perPage: Int = 20): List<AnimeCard> {
        val vars = AniListClient.variablesOf(mapOf("page" to page, "perPage" to perPage))
        val resp = client.post(Queries.POPULAR, vars)

        val media = resp.data
            ?.get("Page")?.jsonObject
            ?.get("media")?.jsonArray
            ?: return emptyList()

        return media.mapNotNull { m ->
            val obj = m.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: return@mapNotNull null
            val titleObj = obj["title"]?.jsonObject
            val title = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
                ?: "Unknown"
            val cover = obj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull
            rememberBasicInfo(id, title)
            AnimeCard(id, title, cover)
        }
    }

    suspend fun details(id: Int): AnimeDetails? {
        val vars = AniListClient.variablesOf(mapOf("id" to id))

        // Primary: AniList
        val media = try {
            val resp = client.post(Queries.DETAILS, vars)
            resp.data?.get("Media")?.jsonObject
        } catch (_: Throwable) {
            null
        }

        if (media == null) {
            // Fallback: TMDB (best-effort) using cached title data.
            val basic = basicInfoCache[id] ?: return null
            return runCatching {
                TmdbMetadataRepository.getDetails(
                    anilistId = id,
                    title = basic.title,
                    seasonYear = basic.seasonYear
                )
            }.getOrNull()
        }

        val titleObj = media["title"]?.jsonObject
        val title = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
            ?: titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
            ?: "Unknown"

        val genres = media["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

        // Remember for future TMDB fallback.
        rememberBasicInfo(id, title, media["seasonYear"]?.jsonPrimitive?.intOrNull)

        return AnimeDetails(
            id = id,
            title = title,
            description = media["description"]?.jsonPrimitive?.contentOrNull,
            bannerUrl = media["bannerImage"]?.jsonPrimitive?.contentOrNull,
            coverUrl = media["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.contentOrNull,
            genres = genres,
            averageScore = media["averageScore"]?.jsonPrimitive?.intOrNull,
            episodes = media["episodes"]?.jsonPrimitive?.intOrNull,
            format = media["format"]?.jsonPrimitive?.contentOrNull,
            status = media["status"]?.jsonPrimitive?.contentOrNull,
            season = media["season"]?.jsonPrimitive?.contentOrNull,
            seasonYear = media["seasonYear"]?.jsonPrimitive?.intOrNull
        )
    }

    suspend fun searchPage(query: String, page: Int = 1, perPage: Int = 36): PageResult<AnimeCard> {
        val vars = AniListClient.variablesOf(mapOf("page" to page, "perPage" to perPage, "search" to query))
        val resp = client.post(Queries.SEARCH, vars)

        val pageObj = resp.data
            ?.get("Page")?.jsonObject
            ?: return PageResult(emptyList(), currentPage = page, hasNextPage = false)

        val pageInfo = pageObj["pageInfo"]?.jsonObject
        val current = pageInfo?.get("currentPage")?.jsonPrimitive?.intOrNull ?: page
        val hasNext = pageInfo?.get("hasNextPage")?.jsonPrimitive?.contentOrNull == "true"

        val media = pageObj["media"]?.jsonArray ?: emptyList()

        val items = media.mapNotNull { m ->
            val obj = m.jsonObject
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val titleObj = obj["title"]?.jsonObject
            val title = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
                ?: "Unknown"
            val cover = obj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull
            rememberBasicInfo(id, title)
            AnimeCard(id, title, cover)
        }

        return PageResult(items = items, currentPage = current, hasNextPage = hasNext)
    }

    // Backwards-compatible helper.
    suspend fun search(query: String, page: Int = 1, perPage: Int = 36): List<AnimeCard> {
        return searchPage(query = query, page = page, perPage = perPage).items
    }
}
