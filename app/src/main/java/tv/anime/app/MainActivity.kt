package tv.anime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import tv.anime.app.data.AnimeRepository
import tv.anime.app.data.StartupImagePreloader
import tv.anime.app.data.StartupPreloadCache
import tv.anime.app.data.anilist.AniListClient
import tv.anime.app.data.provider.DemoEpisodeProvider
import tv.anime.app.data.tmdb.HeroBackdropRepository
import tv.anime.app.data.tmdb.TitleLogoRepository
import tv.anime.app.ui.DetailsScreen
import tv.anime.app.ui.HomeScreen
import tv.anime.app.ui.PlayerScreen
import tv.anime.app.ui.SearchScreen
import tv.anime.app.ui.SideDest
import tv.anime.app.ui.SideMenuScaffold
import tv.anime.app.ui.SimpleTextScreen
import tv.anime.app.ui.StartupLoadingScreen
import tv.anime.app.ui.theme.AnimeTvTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val repository = AnimeRepository(AniListClient())
    private val episodeProvider = DemoEpisodeProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AnimeTvTheme {
                val context = LocalContext.current

                // Gate the app UI behind a branded startup loading screen.
                // We preload the first batch of home content + TMDB artwork lookups.
                var booting by rememberSaveable { mutableStateOf(true) }
                var warmupUrls by remember { mutableStateOf<List<String>>(emptyList()) }

                LaunchedEffect(Unit) {
                    // If we've already preloaded during this process lifetime, don't do it again.
                    if (StartupPreloadCache.home != null) {
                        booting = false
                        return@LaunchedEffect
                    }

                    runCatching {
                        coroutineScope {
                            val heroD = async { repository.heroTrending() }
                            val recentD = async { repository.recent() }
                            val trendingD = async { repository.trending() }
                            val popularD = async { repository.popular() }

                            val hero = heroD.await()
                            val recent = recentD.await()
                            val trending = trendingD.await()
                            val popular = popularD.await()

                            StartupPreloadCache.PreloadedHome(
                                hero = hero,
                                recent = recent,
                                trending = trending,
                                popular = popular
                            )
                        }
                    }
                        .onSuccess { preloaded ->
                            // Store for HomeViewModel.
                            StartupPreloadCache.home = preloaded

                            // Warm up TMDB lookups for hero (logos + high-quality backdrops).
                            // This prevents the hero from "popping" a few seconds after the UI appears.
                            runCatching {
                                coroutineScope {
                                    val heroes = preloaded.hero.toTypedArray()
                                    async { TitleLogoRepository.prefetch(*heroes) }
                                    async { HeroBackdropRepository.prefetch(*heroes) }
                                }
                            }

                            // Build warm-up URLs (used by the splash's hidden AsyncImage warmup).
                            // Include both AniList and TMDB-derived URLs; the latter are what the hero
                            // actually renders on TV when available.
                            warmupUrls = buildList {
                                addAll(preloaded.hero.mapNotNull { it.bannerUrl })
                                addAll(preloaded.hero.mapNotNull { it.coverUrl })
                                // TMDB-derived assets (if available) are what the hero actually renders.
                                addAll(preloaded.hero.mapNotNull { h -> HeroBackdropRepository.peekBackdropUrl(h.id) })
                                addAll(preloaded.hero.mapNotNull { h -> TitleLogoRepository.peekLogoUrl(h.id) })
                                addAll(preloaded.recent.take(18).mapNotNull { it.coverUrl })
                                addAll(preloaded.trending.take(18).mapNotNull { it.coverUrl })
                                addAll(preloaded.popular.take(18).mapNotNull { it.coverUrl })
                            }

                            // Deterministic pre-decode into Coil cache so hero crossfades and initial
                            // rows do not decode mid-animation.
                            runCatching {
                                val dm = context.resources.displayMetrics
                                val heroW = dm.widthPixels.coerceAtLeast(1)
                                val heroH = (dm.heightPixels * 0.70f).roundToInt().coerceAtLeast(1)
                                val posterW = (130f * dm.density).roundToInt().coerceAtLeast(1)
                                val posterH = (195f * dm.density).roundToInt().coerceAtLeast(1)

                                val sizes = StartupImagePreloader.Sizes(
                                    heroWidthPx = heroW,
                                    heroHeightPx = heroH,
                                    posterWidthPx = posterW,
                                    posterHeightPx = posterH
                                )

                                // Use a timeout so slow connections don't trap the user on the splash.
                                withTimeoutOrNull(9_000) {
                                    StartupImagePreloader.preloadHome(context, preloaded, sizes)
                                }
                            }
                        }
                        .onFailure {
                            // If preloading fails (network/JSON), don't strand the user on the splash.
                            StartupPreloadCache.clear()
                        }
                    booting = false
                }

                if (booting) {
                    StartupLoadingScreen(brand = "Yuki", warmupUrls = warmupUrls)
                    return@AnimeTvTheme
                }

                val nav = rememberNavController()

                var menuOpen by rememberSaveable { mutableStateOf(false) }

                val backStackEntry by nav.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route

                // Determine which "root" destination we are currently on (home/search/settings...).
                val rootRoute = remember(route) { route?.substringBefore("/") }
                val rootTabs = remember {
                    setOf("home", "search", "mylist", "addons", "settings")
                }
                val isRoot = rootRoute in rootTabs
                val selectedDest = when (rootRoute) {
                    "search" -> SideDest.Search
                    "mylist" -> SideDest.MyList
                    "addons" -> SideDest.Addons
                    "settings" -> SideDest.Settings
                    else -> SideDest.Home
                }

                // Side menu should only be present on the main tab pages.
                // Details/player should be true full-screen.
                val showSideMenu = isRoot
                if (!showSideMenu) {
                    // Ensure we never keep the menu state around when leaving the root tabs.
                    menuOpen = false
                }

                // Back behavior is handled by SideMenuScaffold when the side menu is present.
                // For fullscreen routes (details/player) we rely on Navigation's back-stack,
                // but keep a defensive handler so remotes that only deliver system BACK still
                // exit those screens reliably.
                if (!showSideMenu) {
                    BackHandler {
                        nav.popBackStack()
                    }
                }

                val onSelectTab: (SideDest) -> Unit = { dest ->
                    val targetRoute = when (dest) {
                        SideDest.Home -> "home"
                        SideDest.Search -> "search"
                        SideDest.MyList -> "mylist"
                        SideDest.Addons -> "addons"
                        SideDest.Settings -> "settings"
                    }
                    nav.navigate(targetRoute) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                val fullscreenContentRootRequester = remember { FocusRequester() }

                if (showSideMenu) {
                    SideMenuScaffold(
                        menuOpen = menuOpen,
                        // SideMenuScaffold is controlled by the caller. Wire it to state so
                        // Back/menu actions can actually open/close the rail on real devices.
                        onMenuOpenChange = { isOpen -> menuOpen = isOpen },
                        selected = selectedDest,
                        onSelect = onSelectTab,
                        backHandlerKey = route ?: "no_route"
                    ) { openMenu, rememberLastContentFocus, contentRootRequester ->
                        AppNavHost(
                            nav = nav,
                            repository = repository,
                            episodeProvider = episodeProvider,
                            openMenu = openMenu,
                            rememberLastContentFocus = rememberLastContentFocus,
                            contentRootRequester = contentRootRequester,
                            // Used by the Search screen's on-screen Back button.
                            requestOpenMenu = { menuOpen = true }
                        )
                    }
                } else {
                    // Fullscreen routes (details/player): no side menu overlay.
                    AppNavHost(
                        nav = nav,
                        repository = repository,
                        episodeProvider = episodeProvider,
                        openMenu = { /* no-op */ },
                        rememberLastContentFocus = { /* no-op */ },
                        contentRootRequester = fullscreenContentRootRequester,
                        requestOpenMenu = { /* no-op */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    nav: NavHostController,
    repository: AnimeRepository,
    episodeProvider: DemoEpisodeProvider,
    openMenu: () -> Unit,
    rememberLastContentFocus: (FocusRequester) -> Unit,
    contentRootRequester: FocusRequester,
    requestOpenMenu: () -> Unit
) {
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                repository = repository,
                onOpenMenu = openMenu,
                rememberLastContentFocus = rememberLastContentFocus,
                contentRootFocusRequester = contentRootRequester,
                onOpenDetails = { id -> nav.navigate("details/$id") }
            )
        }
        composable("search") {
            SearchScreen(
                repository = repository,
                // The on-screen Back button should behave like the hardware Back on TV: open menu.
                onBack = requestOpenMenu,
                onOpenDetails = { id -> nav.navigate("details/$id") },
                onOpenMenu = openMenu,
                rememberLastContentFocus = rememberLastContentFocus
            )
        }
        composable("mylist") {
            SimpleTextScreen(title = "My List")
        }
        composable("addons") {
            SimpleTextScreen(title = "Addons")
        }
        composable("settings") {
            SimpleTextScreen(title = "Settings")
        }
        composable("details/{id}") { backStack ->
            val id = backStack.arguments?.getString("id")?.toIntOrNull() ?: return@composable
            DetailsScreen(
                repository = repository,
                episodeProvider = episodeProvider,
                anilistId = id,
                onBack = { nav.popBackStack() },
                onPlayEpisode = { mediaId, ep ->
                    nav.navigate("player/$mediaId/${ep.number}")
                }
            )
        }
        composable("player/{id}/{ep}") { backStack ->
            val id = backStack.arguments?.getString("id")?.toIntOrNull() ?: return@composable
            val epNum = backStack.arguments?.getString("ep")?.toIntOrNull() ?: 1
            PlayerScreen(
                episodeProvider = episodeProvider,
                anilistId = id,
                episodeNumber = epNum,
                onExit = { nav.popBackStack() }
            )
        }
    }
}
