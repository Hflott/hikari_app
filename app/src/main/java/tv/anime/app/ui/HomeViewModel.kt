package tv.anime.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.anime.app.data.AnimeRepository
import tv.anime.app.data.StartupPreloadCache
import tv.anime.app.domain.AnimeCard
import tv.anime.app.domain.AnimeHero

data class HomeState(
    val isLoading: Boolean = true,
    val hero: List<AnimeHero> = emptyList(),
    val recent: List<AnimeCard> = emptyList(),
    val trending: List<AnimeCard> = emptyList(),
    val popular: List<AnimeCard> = emptyList(),
    val error: String? = null
)

class HomeViewModel(private val repo: AnimeRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        val cached = StartupPreloadCache.home
        if (cached != null) {
            _state.value = HomeState(
                isLoading = false,
                hero = cached.hero,
                recent = cached.recent,
                trending = cached.trending,
                popular = cached.popular,
                error = null
            )
        } else {
            refresh()
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.value = HomeState(isLoading = true)
        runCatching {
            kotlinx.coroutines.coroutineScope {
                val hero = async { repo.heroTrending() }
                val recent = async { repo.recent() }
                val trending = async { repo.trending() }
                val popular = async { repo.popular() }
                listOf(hero.await(), recent.await(), trending.await(), popular.await())
            }
        }
            .onSuccess { results ->
                val hero = results[0] as List<AnimeHero>
                val recent = results[1] as List<AnimeCard>
                val trending = results[2] as List<AnimeCard>
                val popular = results[3] as List<AnimeCard>
                _state.value = HomeState(
                    isLoading = false,
                    hero = hero,
                    recent = recent,
                    trending = trending,
                    popular = popular
                )
            }
            .onFailure { _state.value = HomeState(isLoading = false, error = it.message) }
    }

    companion object {
        fun factory(repo: AnimeRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(repo) as T
            }
        }
    }
}
