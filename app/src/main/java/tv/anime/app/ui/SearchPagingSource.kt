package tv.anime.app.ui

import androidx.paging.PagingSource
import androidx.paging.PagingState
import tv.anime.app.data.AnimeRepository
import tv.anime.app.domain.AnimeCard

/**
 * PagingSource for AniList search.
 *
 * Uses the repository's paging primitives and converts them into Paging 3's load API.
 */
class SearchPagingSource(
    private val repo: AnimeRepository,
    private val query: String,
    private val perPage: Int = 36
) : PagingSource<Int, AnimeCard>() {

    override fun getRefreshKey(state: PagingState<Int, AnimeCard>): Int? {
        // Try to restore around the user's current position.
        val anchor = state.anchorPosition ?: return null
        val closest = state.closestPageToPosition(anchor)
        return closest?.prevKey?.plus(1) ?: closest?.nextKey?.minus(1)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AnimeCard> {
        val page = params.key ?: 1
        return runCatching {
            val res = repo.searchPage(query = query, page = page, perPage = perPage)
            val nextKey = if (res.hasNextPage) page + 1 else null
            val prevKey = if (page > 1) page - 1 else null
            LoadResult.Page(
                data = res.items,
                prevKey = prevKey,
                nextKey = nextKey
            )
        }.getOrElse { t ->
            LoadResult.Error(t)
        }
    }
}
