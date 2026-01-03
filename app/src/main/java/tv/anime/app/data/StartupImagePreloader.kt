package tv.anime.app.data

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tv.anime.app.data.tmdb.HeroBackdropRepository
import tv.anime.app.data.tmdb.TitleLogoRepository
import tv.anime.app.domain.AnimeHero
import tv.anime.app.ui.AllowHardwareBitmaps

/**
 * Best-effort image warm-up to eliminate visible pop-in on Android TV.
 *
 * This explicitly decodes images at the sizes used by the UI (hero backdrop, poster tile, title logo),
 * so the first paint and subsequent crossfades are more likely to hit memory cache rather than
 * doing a decode during an animation.
 */
object StartupImagePreloader {

    data class Sizes(
        val heroWidthPx: Int,
        val heroHeightPx: Int,
        val posterWidthPx: Int,
        val posterHeightPx: Int,
        val logoWidthPx: Int = 520,
        val logoHeightPx: Int = 220
    )

    /**
     * Preload hero backdrops + logos, plus the first batch of posters that are typically visible
     * when Home is entered.
     */
    suspend fun preloadHome(context: Context, home: StartupPreloadCache.PreloadedHome, sizes: Sizes) {
        val hero = home.hero

        // Resolve TMDB artwork URLs first (metadata lookup). Do this concurrently.
        val resolved = coroutineScope {
            hero.map { h ->
                async {
                    ResolvedHero(
                        hero = h,
                        backdropUrl = runCatching { HeroBackdropRepository.getBackdropUrl(h) }.getOrNull(),
                        logoUrl = runCatching { TitleLogoRepository.getLogoUrl(h) }.getOrNull()
                    )
                }
            }.awaitAll()
        }

        // Determine which poster images to warm (avoid overfetching).
        val postersToWarm = buildList {
            // These rows are immediately visible on Home.
            addAll(home.recent.take(14).mapNotNull { it.coverUrl })
            addAll(home.trending.take(14).mapNotNull { it.coverUrl })
            addAll(home.popular.take(14).mapNotNull { it.coverUrl })
        }

        // Build final warm-up jobs.
        val heroBackdropUrls = resolved.mapNotNull { it.backdropUrl ?: it.hero.bannerUrl ?: it.hero.coverUrl }
        val heroLogoUrls = resolved.mapNotNull { it.logoUrl }

        val jobs = buildList<WarmJob> {
            // Hero: decode to hero viewport size.
            heroBackdropUrls.distinct().forEach { url ->
                add(WarmJob(url, sizes.heroWidthPx, sizes.heroHeightPx))
            }
            // Logos: decode to a stable logo size used by the hero.
            heroLogoUrls.distinct().forEach { url ->
                add(WarmJob(url, sizes.logoWidthPx, sizes.logoHeightPx))
            }
            // Posters: decode to tile size.
            postersToWarm.filter { it.isNotBlank() }.distinct().forEach { url ->
                add(WarmJob(url, sizes.posterWidthPx, sizes.posterHeightPx))
            }
        }

        warm(context, jobs)
    }

    /**
     * Preload the current/adjacent hero assets so DPAD navigation and auto-advance do not pop.
     */
    suspend fun preloadHeroNeighborhood(
        context: Context,
        heroes: List<AnimeHero>,
        indices: List<Int>,
        sizes: Sizes
    ) {
        if (heroes.isEmpty()) return

        val chosen = indices.mapNotNull { i -> heroes.getOrNull(i) }.distinctBy { it.id }

        val resolved = coroutineScope {
            chosen.map { h ->
                async {
                    ResolvedHero(
                        hero = h,
                        backdropUrl = runCatching { HeroBackdropRepository.getBackdropUrl(h) }.getOrNull(),
                        logoUrl = runCatching { TitleLogoRepository.getLogoUrl(h) }.getOrNull()
                    )
                }
            }.awaitAll()
        }

        val jobs = buildList<WarmJob> {
            resolved
                .mapNotNull { it.backdropUrl ?: it.hero.bannerUrl ?: it.hero.coverUrl }
                .distinct()
                .forEach { add(WarmJob(it, sizes.heroWidthPx, sizes.heroHeightPx)) }
            resolved
                .mapNotNull { it.logoUrl }
                .distinct()
                .forEach { add(WarmJob(it, sizes.logoWidthPx, sizes.logoHeightPx)) }
        }

        warm(context, jobs)
    }

    private data class ResolvedHero(
        val hero: AnimeHero,
        val backdropUrl: String?,
        val logoUrl: String?
    )

    private data class WarmJob(val url: String, val wPx: Int, val hPx: Int)

    private suspend fun warm(context: Context, jobs: List<WarmJob>) {
        if (jobs.isEmpty()) return

        val imageLoader = SingletonImageLoader.get(context)

        // TV devices can be bandwidth constrained; keep concurrency modest.
        val gate = Semaphore(permits = 4)

        coroutineScope {
            jobs.take(64) // hard cap to avoid stranding the user on slow connections
                .map { job ->
                    async(Dispatchers.IO) {
                        if (job.url.isBlank()) return@async
                        gate.withPermit {
                            val req = ImageRequest.Builder(context)
                                .data(job.url)
                                .size(job.wPx.coerceAtLeast(1), job.hPx.coerceAtLeast(1))
                                .precision(Precision.EXACT)
                                .allowHardware(AllowHardwareBitmaps)
                                .build()

                            // Execute to force a decode (warm memory cache). Errors are ignored.
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    imageLoader.execute(req)
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }
    }
}
