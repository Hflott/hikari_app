package tv.anime.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import tv.anime.app.data.AnimeRepository
import tv.anime.app.domain.AnimeCard

data class SearchState(
    val query: String = "",
    val committedQuery: String = ""
)

class SearchViewModel(private val repo: AnimeRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    /**
     * Paging stream driven by the *committed* query (explicit Search action).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<PagingData<AnimeCard>> = _state
        .map { it.committedQuery.trim() }
        .flatMapLatest { q ->
            if (q.length < 2) {
                // Empty paging data keeps the UI simple for short queries.
                PagingData.empty<AnimeCard>().let { empty ->
                    kotlinx.coroutines.flow.flowOf(empty)
                }
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 36,
                        prefetchDistance = 12,
                        initialLoadSize = 36,
                        enablePlaceholders = false
                    )
                ) {
                    SearchPagingSource(repo = repo, query = q, perPage = 36)
                }.flow
            }
        }
        .cachedIn(viewModelScope)
        .onStart { emit(PagingData.empty()) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, PagingData.empty())

    fun setQuery(newQuery: String) {
        // Only update the text state while the user types.
        // We intentionally do NOT trigger a network search here to avoid
        // rate limiting (AniList) and unnecessary paging source churn.
        _state.value = _state.value.copy(query = newQuery)
    }

    fun commitSearch() {
        val q = _state.value.query.trim()
        _state.value = _state.value.copy(committedQuery = q)
    }

    fun clear() {
        _state.value = SearchState()
    }

    companion object {
        fun factory(repo: AnimeRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(repo) as T
            }
        }
    }
}
