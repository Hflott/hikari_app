package tv.anime.app.data.anilist

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tv.anime.app.domain.Episode
import tv.anime.app.domain.EpisodeProvider
import tv.anime.app.domain.Season
import tv.anime.app.domain.StreamInfo

class AniListEpisodeProvider(
    private val client: AniListClient
) : EpisodeProvider {

    override suspend fun getSeasons(rootMediaId: Int): List<Season> {
        val res = client.post(
            query = Queries.SEASONS,
            variables = AniListClient.variablesOf(mapOf("id" to rootMediaId))
        )

        val media = res.data?.get("Media")?.jsonObject ?: return emptyList()

        val selfSeason = media.toSeasonOrNull() ?: return emptyList()
        val related = extractRelatedSeasons(media)

        val ordered = orderSeasonsHeuristic(selfSeason, related)

        return ordered.mapIndexed { idx, s ->
            s.copy(label = "Season ${idx + 1} • ${s.label}")
        }
    }

    override suspend fun getEpisodes(anilistMediaId: Int): List<Episode> {
        val res = client.post(
            query = Queries.DETAILS,
            variables = AniListClient.variablesOf(mapOf("id" to anilistMediaId))
        )
        val media = res.data?.get("Media")?.jsonObject ?: return emptyList()

        val count = media["episodes"]?.jsonPrimitive?.content?.toIntOrNull()
        val safeCount = count ?: 0

        return (1..safeCount).map { n ->
            Episode(
                number = n,
                title = "Episode $n",
                sourceId = n.toString()
            )
        }
    }

    override suspend fun resolveStream(
        anilistMediaId: Int,
        episode: Episode
    ): StreamInfo {
        // Placeholder stream: replace with your licensed backend later
        return StreamInfo(
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            headers = emptyMap()
        )
    }

    private fun JsonObject.toSeasonOrNull(): Season? {
        val id = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val title = this["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.content ?: "Unknown"
        val year = this["seasonYear"]?.jsonPrimitive?.content?.toIntOrNull()
        val eps = this["episodes"]?.jsonPrimitive?.content?.toIntOrNull()
        val cover = this["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content

        val label = buildString {
            append(title)
            if (year != null) append(" ($year)")
            if (eps != null) append(" • $eps eps")
        }

        return Season(mediaId = id, label = label, coverUrl = cover, episodesCount = eps)
    }

    private fun extractRelatedSeasons(media: JsonObject): List<Pair<String, Season>> {
        val edgesArray: JsonArray =
            media["relations"]
                ?.jsonObject?.get("edges")
                ?.let { it as? JsonArray }
                ?: return emptyList()

        val out = mutableListOf<Pair<String, Season>>()

        for (edgeElement in edgesArray) {
            val edge = edgeElement.jsonObject
            val relType = edge["relationType"]?.jsonPrimitive?.content ?: continue
            val node = edge["node"]?.jsonObject ?: continue

            // filter to anime entries only
            val type = node["type"]?.jsonPrimitive?.content
            if (type != "ANIME") continue

            // keep TV/TV_SHORT as “seasons” by default
            val format = node["format"]?.jsonPrimitive?.content
            if (format != "TV" && format != "TV_SHORT") continue

            val season = node.toSeasonOrNull() ?: continue
            out += relType to season
        }

        return out
    }

    private fun orderSeasonsHeuristic(
        self: Season,
        related: List<Pair<String, Season>>
    ): List<Season> {
        val prequels = related.filter { it.first == "PREQUEL" }.map { it.second }
        val sequels = related.filter { it.first == "SEQUEL" }.map { it.second }

        fun Season.yearInLabelOrMin(): Int =
            Regex("""\((\d{4})\)""").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MIN_VALUE

        fun Season.yearInLabelOrMax(): Int =
            Regex("""\((\d{4})\)""").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

        val orderedPre = prequels.sortedBy { it.yearInLabelOrMin() }
        val orderedSeq = sequels.sortedBy { it.yearInLabelOrMax() }

        return (orderedPre + self + orderedSeq).distinctBy { it.mediaId }
    }
}