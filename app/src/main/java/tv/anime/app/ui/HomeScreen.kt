package tv.anime.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.anime.app.data.AnimeRepository

@Composable
fun HomeScreen(
    repository: AnimeRepository,
    onOpenMenu: () -> Unit,
    rememberLastContentFocus: (androidx.compose.ui.focus.FocusRequester) -> Unit,
    contentRootFocusRequester: androidx.compose.ui.focus.FocusRequester,
    onOpenDetails: (Int) -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
    HomeContent(
        state = vm.state.collectAsState().value,
        onRefresh = vm::refresh,
        onOpenMenu = onOpenMenu,
        rememberLastContentFocus = rememberLastContentFocus,
        contentRootFocusRequester = contentRootFocusRequester,
        onOpenDetails = onOpenDetails
    )
}
