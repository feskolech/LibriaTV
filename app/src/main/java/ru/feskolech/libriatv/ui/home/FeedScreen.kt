package ru.feskolech.libriatv.ui.home

import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.remember
import ru.feskolech.libriatv.ui.components.PosterCard

@Composable
fun FeedScreen(onOpenRelease: (Int) -> Unit, onContentFocus: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()
    val firstPoster = remember { FocusRequester() }
    LaunchedEffect(state.releases.isNotEmpty()) { if (state.releases.isNotEmpty()) firstPoster.requestFocus() }
    LaunchedEffect(gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, state.releases.size) {
        if (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == state.releases.lastIndex) viewModel.loadMore()
    }
    Column(Modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onContentFocus() }.padding(top = 27.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.new_episodes), Modifier.padding(start = 48.dp), color = Color.White)
        if (state.error != null && state.releases.isEmpty()) {
            Text(stringResource(R.string.home_error), Modifier.padding(start = 48.dp))
            Button(onClick = viewModel::loadMore, modifier = Modifier.padding(start = 48.dp)) { Text(stringResource(R.string.retry)) }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), state = gridState,
            modifier = Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            items(state.releases, key = { it.id }) { release ->
                PosterCard(release, release.latestEpisode?.ordinal?.toInt()?.let { stringResource(R.string.episode_number, it) },
                    modifier = if (release.id == state.releases.first().id) Modifier.focusRequester(firstPoster) else Modifier,
                    onFocus = onContentFocus,
                    onClick = { onOpenRelease(release.id) },
                    onLongClick = { onOpenRelease(release.id) })
            }
        }
    }
}
