package ru.feskolech.libriatv.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import androidx.tv.material3.Tab
import androidx.compose.ui.unit.sp
import ru.feskolech.libriatv.domain.UserList
import ru.feskolech.libriatv.ui.components.PosterCard
import ru.feskolech.libriatv.data.repo.latestFavoriteOrdinal
import ru.feskolech.libriatv.ui.components.AlphabetGrid

@Composable
fun FavoritesScreen(onOpenRelease: (Int) -> Unit, onLogin: () -> Unit,
    onContentFocus: () -> Unit, viewModel: FavoritesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val first = remember { FocusRequester() }
    var sortingOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.reload() }
    LaunchedEffect(state.authorized) { withFrameNanos { }; runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().background(Color(0xFF101010))
        .onFocusChanged { if (it.hasFocus) onContentFocus() }.padding(top = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!state.authorized && !state.loading) {
            Text(stringResource(R.string.favorites), Modifier.padding(horizontal = 48.dp),
                style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(stringResource(R.string.favorites_login_hint), Modifier.padding(horizontal = 48.dp), color = Color.White)
            Button(onClick = onLogin, modifier = Modifier.padding(horizontal = 48.dp).focusRequester(first)) {
                Text(stringResource(R.string.auth_sign_in))
            }
        } else {
            // Favorites and the account lists in one place; tabs switch on focus like the schedule.
            val tabs = listOf<UserList?>(null) + UserList.entries
            androidx.tv.material3.TabRow(selectedTabIndex = tabs.indexOf(state.tab).coerceAtLeast(0),
                modifier = Modifier.padding(horizontal = 48.dp).focusRequester(first)) {
                tabs.forEachIndexed { index, tab ->
                    Tab(selected = tab == state.tab, onFocus = { viewModel.selectTab(tab) }) {
                        Text(stringResource(tab.tabLabel()), fontSize = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            if (state.tab == null) Button(onClick = { sortingOpen = true },
                modifier = Modifier.padding(horizontal = 48.dp)) {
                Text(stringResource(R.string.filter_sorting) + ": " +
                    (if (state.selectedSorting == "TITLE_ASC") stringResource(R.string.sort_title)
                        else state.sorting.firstOrNull { it.id == state.selectedSorting }?.title
                        ?: stringResource(R.string.filter_any)))
            }
            when {
                state.releases.isEmpty() && state.loading ->
                    Text(stringResource(R.string.home_loading), Modifier.padding(horizontal = 48.dp), color = Color.White)
                state.releases.isEmpty() && state.error != null -> Column(Modifier.padding(horizontal = 48.dp)) {
                    Text(state.error.orEmpty(), color = Color.White)
                    Button(onClick = viewModel::reload) { Text(stringResource(R.string.retry)) }
                }
                state.releases.isEmpty() ->
                    Text(state.tab?.let { stringResource(R.string.list_empty, stringResource(it.tabLabel())) }
                        ?: stringResource(R.string.favorites_empty), Modifier.padding(horizontal = 48.dp), color = Color.White)
                state.tab == null && state.selectedSorting == "TITLE_ASC" -> AlphabetGrid(state.releases,
                    badge = { it.latestFavoriteOrdinal()?.let { n -> stringResource(R.string.episode_number, n) } },
                    favoriteIds = state.ids, onOpen = onOpenRelease)
                else -> LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 27.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    itemsIndexed(state.releases, key = { _, release -> release.id }) { index, release ->
                        PosterCard(release, release.latestFavoriteOrdinal()?.let { stringResource(R.string.episode_number, it) },
                            isFavorite = release.id in state.ids,
                            onFocus = { onContentFocus(); if (index >= state.releases.size - 10) viewModel.loadMore() },
                            onClick = { onOpenRelease(release.id) })
                    }
                }
            }
        }
    }
    if (sortingOpen) {
        val dialogFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = { sortingOpen = false }) {
            Surface {
                Column(Modifier.width(480.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.filter_sorting), style = MaterialTheme.typography.headlineSmall)
                    LazyColumn(Modifier.height(510.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Button(onClick = { viewModel.sort(null); sortingOpen = false },
                                modifier = Modifier.focusRequester(dialogFocus)) {
                                Text(stringResource(R.string.filter_any))
                            }
                        }
                        items(state.sorting) { option ->
                            Button(onClick = { viewModel.sort(option.id); sortingOpen = false }) {
                                Text(option.title)
                            }
                        }
                        item {
                            Button(onClick = { viewModel.sort("TITLE_ASC"); sortingOpen = false }) {
                                Text(stringResource(R.string.sort_title))
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { dialogFocus.requestFocus() } }
    }
}

private fun UserList?.tabLabel(): Int = when (this) {
    null -> R.string.favorites
    UserList.WATCHING -> R.string.list_watching
    UserList.PLANNED -> R.string.list_planned
    UserList.WATCHED -> R.string.list_watched
    UserList.POSTPONED -> R.string.list_postponed
    UserList.ABANDONED -> R.string.list_abandoned
}
