package ru.feskolech.libriatv.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import ru.feskolech.libriatv.ui.components.AlphabetGrid
import ru.feskolech.libriatv.ui.components.LoadingSpinner
import ru.feskolech.libriatv.ui.components.PosterCard

private const val PRELOAD_DISTANCE = 12

/**
 * The catalog's results, shared by both catalog screens: loading, error and empty states, the
 * title-sorted grid with its alphabet, or the endless poster grid. [openedCard] sits on the card with
 * id [lastOpened], so Back from a release page can return to it.
 */
@Composable
internal fun CatalogResults(
    state: CatalogUiState,
    gridState: LazyGridState,
    lastOpened: Int,
    openedCard: FocusRequester,
    onContentFocus: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Int) -> Unit,
    gridModifier: Modifier = Modifier,
) {
    when {
        state.releases.isEmpty() && state.loading -> Row(Modifier.padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadingSpinner(size = 22.dp)
            Text(state.titleProgress?.let { (done, total) -> stringResource(R.string.catalog_title_progress, done, total) }
                ?: stringResource(R.string.home_loading), color = Color.LightGray)
        }
        state.releases.isEmpty() && state.error != null -> Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.error.orEmpty(), color = Color.White)
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        state.releases.isEmpty() ->
            Text(stringResource(R.string.catalog_empty), Modifier.padding(horizontal = 48.dp), color = Color.LightGray)
        state.titleSorted -> AlphabetGrid(state.releases, badge = { it.year?.toString() },
            openedId = lastOpened, openedFocus = openedCard, onOpen = onOpen)
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            state = gridState,
            modifier = gridModifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 27.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            itemsIndexed(state.releases, key = { _, r -> r.id }) { index, release ->
                PosterCard(
                    release,
                    badge = release.year?.toString(),
                    onFocus = {
                        onContentFocus()
                        if (index >= state.releases.size - PRELOAD_DISTANCE) onLoadMore()
                    },
                    modifier = if (release.id == lastOpened) Modifier.focusRequester(openedCard) else Modifier,
                    onClick = { onOpen(release.id) },
                )
            }
        }
    }
}
