package ru.feskolech.libriatv.ui.catalog

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.focusProperties
import ru.feskolech.libriatv.ui.components.DialogButton
import ru.feskolech.libriatv.ui.components.AppDialog
import ru.feskolech.libriatv.ui.components.DrawerBrowsing
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.WideButtonScale
import ru.feskolech.libriatv.domain.CatalogFilter
import ru.feskolech.libriatv.domain.FilterOption
import ru.feskolech.libriatv.ui.components.PosterCard
import ru.feskolech.libriatv.ui.components.AlphabetGrid

private enum class FilterKind { Genres, Types, Seasons, Years, Statuses, Sorting }

@Composable
fun CatalogScreen(
    onOpenRelease: (Int) -> Unit,
    onContentFocus: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var dialog by remember { mutableStateOf<FilterKind?>(null) }
    val firstFilter = remember { FocusRequester() }
    val filter = state.filter
    val refs = state.references

    // Back from a release page returns to the card that was opened (the grid keeps its scroll
    // position); otherwise the screen starts on the first filter.
    var lastOpened by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(-1) }
    val openedCard = remember { FocusRequester() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(refs != null) {
        withFrameNanos { }
        if (DrawerBrowsing.active) return@LaunchedEffect
        val restored = lastOpened >= 0 && state.releases.any { it.id == lastOpened } &&
            runCatching { openedCard.requestFocus() }.isSuccess
        if (!restored) runCatching { firstFilter.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onFocusChanged { if (it.hasFocus) onContentFocus() }
            .padding(top = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.catalog), Modifier.padding(horizontal = 48.dp),
            style = MaterialTheme.typography.headlineLarge, color = Color.White)

        if (refs != null) {
            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    FilterButton(stringResource(R.string.filter_genres), filter.genres.size, Modifier.focusRequester(firstFilter)) {
                        dialog = FilterKind.Genres
                    }
                }
                item { FilterButton(stringResource(R.string.filter_types), filter.types.size) { dialog = FilterKind.Types } }
                item { FilterButton(stringResource(R.string.filter_seasons), filter.seasons.size) { dialog = FilterKind.Seasons } }
                item {
                    val years = when {
                        filter.fromYear != null || filter.toYear != null ->
                            "${filter.fromYear ?: refs.years.lastOrNull() ?: ""}–${filter.toYear ?: refs.years.firstOrNull() ?: ""}"
                        else -> null
                    }
                    Button(onClick = { dialog = FilterKind.Years }) {
                        Text(listOfNotNull(stringResource(R.string.filter_years), years).joinToString(": "))
                    }
                }
                item { FilterButton(stringResource(R.string.filter_status), filter.statuses.size) { dialog = FilterKind.Statuses } }
                item {
                    val sortTitle = if (state.titleSorted) stringResource(R.string.sort_title)
                        else refs.sorting.firstOrNull { it.id == filter.sorting }?.title
                    Button(onClick = { dialog = FilterKind.Sorting }) {
                        Text(listOfNotNull(stringResource(R.string.filter_sorting), sortTitle).joinToString(": "))
                    }
                }
                item { Button(onClick = { viewModel.random(onOpenRelease) }) { Text(stringResource(R.string.random_release)) } }
                if (filter.activeCount > 0) {
                    item { Button(onClick = viewModel::resetFilter) { Text(stringResource(R.string.filter_reset)) } }
                }
            }
        }

        when {
            state.releases.isEmpty() && state.loading ->
                Text(state.titleProgress?.let { (done, total) -> stringResource(R.string.catalog_title_progress, done, total) }
                    ?: stringResource(R.string.home_loading), Modifier.padding(horizontal = 48.dp), color = Color.LightGray)
            state.releases.isEmpty() && state.error != null -> Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.error.orEmpty(), color = Color.White)
                Button(onClick = viewModel::reload) { Text(stringResource(R.string.retry)) }
            }
            state.releases.isEmpty() ->
                Text(stringResource(R.string.catalog_empty), Modifier.padding(horizontal = 48.dp), color = Color.LightGray)
            state.titleSorted -> AlphabetGrid(state.releases, badge = { it.year?.toString() },
                onOpen = onOpenRelease)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize().focusRestorer(),
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
                            if (index >= state.releases.size - PRELOAD_DISTANCE) viewModel.loadMore()
                        },
                        modifier = if (release.id == lastOpened) Modifier.focusRequester(openedCard) else Modifier,
                        onClick = { lastOpened = release.id; onOpenRelease(release.id) },
                    )
                }
            }
        }
    }

    val close = { dialog = null }
    if (refs != null) when (dialog) {
        FilterKind.Genres -> MultiSelectDialog(stringResource(R.string.filter_genres), refs.genres, filter.genres, close) {
            viewModel.applyFilter(filter.copy(genres = it))
        }
        FilterKind.Types -> MultiSelectDialog(stringResource(R.string.filter_types), refs.types, filter.types, close) {
            viewModel.applyFilter(filter.copy(types = it))
        }
        FilterKind.Seasons -> MultiSelectDialog(stringResource(R.string.filter_seasons), refs.seasons, filter.seasons, close) {
            viewModel.applyFilter(filter.copy(seasons = it))
        }
        FilterKind.Statuses -> MultiSelectDialog(stringResource(R.string.filter_status), refs.statuses, filter.statuses, close) {
            viewModel.applyFilter(filter.copy(statuses = it))
        }
        FilterKind.Sorting -> SingleSelectDialog(stringResource(R.string.filter_sorting),
            refs.sorting + FilterOption("TITLE_ASC", stringResource(R.string.sort_title)),
            if (state.titleSorted) "TITLE_ASC" else filter.sorting, close) {
            if (it == "TITLE_ASC") viewModel.sortByTitle() else viewModel.sortByApi(it)
        }
        FilterKind.Years -> YearsDialog(refs.years, filter, close) { from, to ->
            viewModel.applyFilter(filter.copy(fromYear = from, toYear = to))
        }
        null -> Unit
    }
}

private const val PRELOAD_DISTANCE = 12

@Composable
private fun FilterButton(title: String, count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) { Text(if (count > 0) "$title ($count)" else title) }
}

@Composable
private fun FilterDialog(title: String, onDismiss: () -> Unit,
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null, content: @Composable () -> Unit) {
    AppDialog(onDismiss = onDismiss, title = title, actions = actions) { content() }
}

@Composable
private fun MultiSelectDialog(
    title: String,
    options: List<FilterOption>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var picked by remember { mutableStateOf(selected) }
    val focus = remember { FocusRequester() }
    val applyFocus = remember { FocusRequester() }
    // Apply and Clear are equal; Down from the end of the list still lands on Apply, not on Clear.
    FilterDialog(title, onDismiss, actions = {
        DialogButton(stringResource(R.string.filter_apply), { onApply(picked); onDismiss() }, modifier = Modifier.focusRequester(applyFocus))
        DialogButton(stringResource(R.string.filter_clear), { picked = emptySet() })
    }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options, key = { it.id }) { option ->
                val on = option.id in picked
                Button(
                    scale = WideButtonScale,
                    onClick = { picked = if (on) picked - option.id else picked + option.id },
                    modifier = Modifier.fillMaxWidth().then(if (option == options.first()) Modifier.focusRequester(focus) else Modifier)
                        .then(if (option == options.last()) Modifier.focusProperties { down = applyFocus } else Modifier),
                ) {
                    Text((if (on) "✓  " else "     ") + option.title)
                }
            }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}

@Composable
private fun SingleSelectDialog(
    title: String,
    options: List<FilterOption>,
    selected: String?,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    FilterDialog(title, onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options, key = { it.id }) { option ->
                val on = option.id == selected
                Button(
                    scale = WideButtonScale,
                    onClick = { onApply(option.id); onDismiss() },
                    modifier = Modifier.fillMaxWidth().then(
                        if (on || (selected == null && option == options.first())) Modifier.focusRequester(focus) else Modifier,
                    ),
                ) { Text((if (on) "✓  " else "     ") + option.title) }
            }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}

@Composable
private fun YearsDialog(years: List<Int>, filter: CatalogFilter, onDismiss: () -> Unit, onApply: (Int?, Int?) -> Unit) {
    var from by remember { mutableStateOf(filter.fromYear) }
    var to by remember { mutableStateOf(filter.toYear) }
    val ascending = remember(years) { years.sorted() }
    val focus = remember { FocusRequester() }
    val applyFocus = remember { FocusRequester() }
    FilterDialog(stringResource(R.string.filter_years), onDismiss, actions = {
        DialogButton(stringResource(R.string.filter_apply), { onApply(from, to); onDismiss() }, modifier = Modifier.focusRequester(applyFocus))
        DialogButton(stringResource(R.string.filter_clear), { from = null; to = null })
    }) {
        Text(stringResource(R.string.filter_year_from), color = Color.LightGray)
        YearRow(ascending, from, Modifier.focusRequester(focus)) { year ->
            from = year
            if (year != null && to != null && to!! < year) to = year
        }
        Text(stringResource(R.string.filter_year_to), color = Color.LightGray)
        YearRow(ascending, to, down = applyFocus) { year ->
            to = year
            if (year != null && from != null && from!! > year) from = year
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}

@Composable
private fun YearRow(years: List<Int>, selected: Int?, modifier: Modifier = Modifier, down: FocusRequester? = null, onPick: (Int?) -> Unit) {
    // [down]: from any year the Down key goes to Apply, wherever that year sits in the row.
    val chip = if (down == null) Modifier else Modifier.focusProperties { this.down = down }
    val start = (years.indexOf(selected).takeIf { it >= 0 } ?: years.lastIndex).coerceAtLeast(0)
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0)),
    ) {
        item { Button(onClick = { onPick(null) }, modifier = chip) { Text(if (selected == null) "✓ " + stringResource(R.string.filter_any) else stringResource(R.string.filter_any)) } }
        items(years) { year -> Button(onClick = { onPick(year) }, modifier = chip) { Text(if (year == selected) "✓ $year" else "$year") } }
    }
}
