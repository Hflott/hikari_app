package tv.anime.app.data

import tv.anime.app.domain.AnimeCard
import tv.anime.app.domain.AnimeHero

/**
 * In-memory cache for app-start preloading.
 *
 * We show a branded startup screen while fetching the first batch of content.
 * HomeViewModel can consume this cache to avoid re-fetching the same lists.
 */
object StartupPreloadCache {

    data class PreloadedHome(
        val hero: List<AnimeHero>,
        val recent: List<AnimeCard>,
        val trending: List<AnimeCard>,
        val popular: List<AnimeCard>
    )

    @Volatile
    var home: PreloadedHome? = null

    fun clear() {
        home = null
    }
}
