package ru.feskolech.libriatv.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.domain.CatalogFilter
import ru.feskolech.libriatv.domain.CatalogReferences
import ru.feskolech.libriatv.domain.FilterOption
import ru.feskolech.libriatv.ui.components.AccentButton
import ru.feskolech.libriatv.ui.components.DialogButton
import ru.feskolech.libriatv.ui.components.DrawerBrowsing

/** The API's own default order; a filter without a sorting means this one. */
internal const val DEFAULT_SORTING = "FRESH_AT_DESC"

/** A ready-made catalog selection: one chip, one press. */
internal data class QuickPick(val label: String, val filter: CatalogFilter)

@Composable
private fun quickPicks(): List<QuickPick> {
    val year = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    return listOf(
        QuickPick(stringResource(R.string.pick_fresh), CatalogFilter(sorting = DEFAULT_SORTING)),
        QuickPick(stringResource(R.string.pick_ongoing), CatalogFilter(statuses = setOf("IS_ONGOING"), sorting = DEFAULT_SORTING)),
        QuickPick(stringResource(R.string.pick_best), CatalogFilter(sorting = "RATING_DESC")),
        QuickPick(stringResource(R.string.pick_year, year), CatalogFilter(fromYear = year, toYear = year, sorting = "RATING_DESC")),
        QuickPick(stringResource(R.string.pick_movies), CatalogFilter(types = setOf("MOVIE"), sorting = "RATING_DESC")),
    )
}

private fun CatalogFilter.normalized() = if (sorting == null) copy(sorting = DEFAULT_SORTING) else this

/**
 * Which catalog to show: this one, or the previous screen when the viewer switched it off in Settings
 * (kept as it was, to fall back to). Both share one [CatalogViewModel].
 */
@Composable
fun CatalogRoute(onOpenRelease: (Int) -> Unit, onContentFocus: () -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    when (viewModel.newCatalog.collectAsState().value) {
        true -> CatalogBrowseScreen(onOpenRelease, onContentFocus, viewModel)
        false -> CatalogScreen(onOpenRelease, onContentFocus, viewModel)
        null -> Unit
    }
}

/**
 * The catalog as quick picks: chips for the common cases ("airing now", "top rated", "movies"…) that switch
 * the grid as the focus moves along them, the viewer's saved filters next to them, "Custom filter" opening
 * one side panel with every filter (applied live, no "Apply"), and "I'm feeling lucky" within what is shown.
 * A custom filter lists its parts as chips above the grid, each removed with one OK, and can be saved as a
 * chip of its own.
 */
@Composable
fun CatalogBrowseScreen(onOpenRelease: (Int) -> Unit, onContentFocus: () -> Unit, viewModel: CatalogViewModel) {
    val state by viewModel.state.collectAsState()
    val refs = state.references
    val picks = quickPicks()
    val filter = state.filter.normalized()
    val pickIndex = if (state.titleSorted) -1 else picks.indexOfFirst { it.filter == filter }
    val savedIndex = if (pickIndex >= 0) -1
        else state.saved.indexOfFirst { it.filter.normalized() == filter && it.titleSorted == state.titleSorted }
    val custom = pickIndex < 0 && savedIndex < 0
    val parts = if (custom && refs != null) filterParts(refs, filter, state.titleSorted) else emptyList()
    var panelOpen by remember { mutableStateOf(false) }

    // Back from a release page returns to the card that was opened; otherwise the screen starts on the
    // selected chip. Entering from the menu also lands there, never on whichever chip sits nearest
    // (the chips switch the grid on focus).
    var lastOpened by rememberSaveable { mutableIntStateOf(-1) }
    val openedCard = remember { FocusRequester() }
    val selectedChip = remember { FocusRequester() }
    val firstAction = remember { FocusRequester() }
    var gridHadFocus by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (DrawerBrowsing.active) return@LaunchedEffect
        // Marked before the request: entering the screen otherwise sends the focus to the chips.
        gridHadFocus = lastOpened >= 0 && state.releases.any { it.id == lastOpened }
        val restored = gridHadFocus && runCatching { openedCard.requestFocus() }.isSuccess
        if (!restored) { gridHadFocus = false; runCatching { selectedChip.requestFocus() } }
    }
    // A chip that removes itself (a filter part, "Save", "Remove") hands the focus on instead of dropping it,
    // once the change has arrived: the saved list is written asynchronously, and a focus that fell on a quick
    // pick meanwhile would switch the grid. After "Save" the focus goes to the new chip.
    var refocus by remember { mutableStateOf<Refocus?>(null) }
    LaunchedEffect(refocus, state.filter, state.titleSorted, state.saved) {
        val target = refocus ?: return@LaunchedEffect
        val arrived = when (target) {
            Refocus.Saved -> savedIndex >= 0
            Refocus.Removed -> savedIndex < 0
            Refocus.Part -> true
        }
        if (!arrived) return@LaunchedEffect
        withFrameNanos { }
        refocus = null
        if (target != Refocus.Part || runCatching { firstAction.requestFocus() }.isFailure) runCatching { selectedChip.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .onFocusChanged { if (it.hasFocus) onContentFocus() }
            .focusProperties { onEnter = { if (!gridHadFocus) runCatching { selectedChip.requestFocus() } } }
            .focusGroup()
            .padding(top = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.catalog), Modifier.padding(horizontal = 48.dp),
            style = MaterialTheme.typography.headlineLarge, color = Color.White)

        // Down from any chip goes to the row of filter parts / actions below when there is one: that row
        // starts at the left, so from a chip further right the nearest thing below would be the grid.
        val actionsShown = parts.isNotEmpty() || savedIndex >= 0
        val down = if (actionsShown) Modifier.focusProperties { this.down = firstAction } else Modifier
        // A plain scrolling Row, not a lazy one: every chip stays composed, so the selected one can always
        // take the focus back (Up from the grid, Back from the panel).
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 48.dp)
            .focusRestorer(selectedChip).focusGroup()
            .onFocusChanged { if (it.hasFocus) gridHadFocus = false },
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // The filter comes first, always in reach; the screen still opens on the selected pick
            // ("Recently updated" by default), not on this chip.
            CatalogChip(stringResource(R.string.catalog_custom) + if (parts.isNotEmpty()) " (${parts.size})" else "",
                selected = custom, icon = Icons.Filled.Tune,
                modifier = down.then(if (custom) Modifier.focusRequester(selectedChip) else Modifier),
                onClick = { if (refs != null) panelOpen = true })
            picks.forEachIndexed { index, pick ->
                CatalogChip(pick.label, selected = index == pickIndex,
                    modifier = down.then(if (index == pickIndex) Modifier.focusRequester(selectedChip) else Modifier),
                    onFocus = { viewModel.pick(pick.filter) }, onClick = {})
            }
            state.saved.forEachIndexed { index, saved ->
                CatalogChip(saved.name, selected = index == savedIndex, icon = Icons.Filled.Bookmark,
                    modifier = down.then(if (index == savedIndex) Modifier.focusRequester(selectedChip) else Modifier),
                    onFocus = { viewModel.pick(saved.filter, saved.titleSorted) }, onClick = {})
            }
            CatalogChip(stringResource(R.string.random_release), selected = false, icon = Icons.Filled.Casino, modifier = down,
                onClick = { viewModel.lucky(onOpenRelease) })
        }

        // The custom filter spelled out: each part is a chip that removes it; the whole can be kept as a
        // chip of its own. A saved chip that is selected can be removed here.
        if (actionsShown) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 48.dp)
            .onFocusChanged { if (it.hasFocus) gridHadFocus = false },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            parts.forEachIndexed { index, part ->
                CatalogChip("${part.label}  ✕", selected = false, small = true,
                    modifier = if (index == 0) Modifier.focusRequester(firstAction) else Modifier,
                    onClick = { viewModel.pick(part.without, part.titleSorted); refocus = Refocus.Part })
            }
            if (parts.isNotEmpty()) CatalogChip(stringResource(R.string.catalog_save), selected = false, small = true,
                icon = Icons.Filled.Bookmark,
                onClick = { refocus = Refocus.Saved; viewModel.saveCurrent(parts.joinToString(" · ") { it.label }) })
            if (savedIndex >= 0) CatalogChip(stringResource(R.string.catalog_remove_saved), selected = false, small = true,
                modifier = Modifier.focusRequester(firstAction),
                onClick = { refocus = Refocus.Removed; viewModel.removeSaved(state.saved[savedIndex]) })
        }

        CatalogResults(state, gridState, lastOpened, openedCard, onContentFocus, viewModel::loadMore, viewModel::reload,
            onOpen = { id -> lastOpened = id; onOpenRelease(id) },
            gridModifier = Modifier.onFocusChanged { if (it.hasFocus) gridHadFocus = true })
    }

    if (panelOpen && refs != null) FilterPanel(refs, filter, state.titleSorted,
        onChange = { changed, byTitle -> viewModel.pick(changed, byTitle) },
        onReset = { viewModel.pick(CatalogFilter(sorting = DEFAULT_SORTING)) },
        onDismiss = { panelOpen = false })
}

/** Where the focus goes after a chip removed itself. */
private enum class Refocus { Part, Saved, Removed }

/** One part of a filter as a chip: its label, and the filter (and title order) without it. */
private data class FilterPart(val label: String, val without: CatalogFilter, val titleSorted: Boolean)

private fun filterParts(refs: CatalogReferences, filter: CatalogFilter, titleSorted: Boolean): List<FilterPart> {
    fun titles(ids: Set<String>, options: List<FilterOption>) = options.filter { it.id in ids }
    val parts = mutableListOf<FilterPart>()
    titles(filter.genres, refs.genres).forEach { parts += FilterPart(it.title, filter.copy(genres = filter.genres - it.id), titleSorted) }
    titles(filter.types, refs.types).forEach { parts += FilterPart(it.title, filter.copy(types = filter.types - it.id), titleSorted) }
    titles(filter.seasons, refs.seasons).forEach { parts += FilterPart(it.title, filter.copy(seasons = filter.seasons - it.id), titleSorted) }
    titles(filter.statuses, refs.statuses).forEach { parts += FilterPart(it.title, filter.copy(statuses = filter.statuses - it.id), titleSorted) }
    if (filter.fromYear != null || filter.toYear != null) {
        val label = if (filter.fromYear == filter.toYear) "${filter.fromYear}" else "${filter.fromYear ?: "…"}–${filter.toYear ?: "…"}"
        parts += FilterPart(label, filter.copy(fromYear = null, toYear = null), titleSorted)
    }
    if (titleSorted) parts += FilterPart("А–Я", filter.copy(sorting = DEFAULT_SORTING), titleSorted = false)
    else if (filter.sorting != DEFAULT_SORTING) refs.sorting.firstOrNull { it.id == filter.sorting }?.let {
        parts += FilterPart(it.title, filter.copy(sorting = DEFAULT_SORTING), titleSorted = false)
    }
    return parts
}

@Composable
private fun CatalogChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null, icon: ImageVector? = null, small: Boolean = false) {
    AccentButton(onClick = onClick, modifier = modifier
        .onFocusChanged { if (it.isFocused) onFocus?.invoke() }
        .then(if (selected) Modifier.border(2.dp, Color(0xFFB4B4BC), RoundedCornerShape(50)) else Modifier)) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(if (small) 18.dp else 22.dp))
        Text(text, fontSize = if (small) 16.sp else 18.sp, maxLines = 1)
    }
}

/**
 * Every filter in one panel on the right, instead of a dialog per filter. Choices apply as they are made
 * (the grid behind follows); "Show" or Back closes the panel.
 */
@Composable
private fun FilterPanel(refs: CatalogReferences, filter: CatalogFilter, titleSorted: Boolean,
    onChange: (CatalogFilter, Boolean) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Only a light dim: the point of the panel is seeing the grid change behind it.
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0.3f) }
        val first = remember { FocusRequester() }
        val showFocus = remember { FocusRequester() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.width(520.dp).fillMaxHeight()
                .background(Color(0xFF1C1C20), RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.catalog_custom), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item {
                        PanelSection(stringResource(R.string.filter_sorting)) {
                            refs.sorting.forEachIndexed { index, option ->
                                PanelChip(option.title, !titleSorted && filter.sorting == option.id,
                                    if (index == 0) Modifier.focusRequester(first) else Modifier) {
                                    onChange(filter.copy(sorting = option.id), false)
                                }
                            }
                            PanelChip(stringResource(R.string.sort_title), titleSorted) { onChange(filter.copy(sorting = null), true) }
                        }
                    }
                    multiSection(R.string.filter_types, refs.types, filter.types) { onChange(filter.copy(types = it), titleSorted) }
                    multiSection(R.string.filter_status, refs.statuses, filter.statuses) { onChange(filter.copy(statuses = it), titleSorted) }
                    multiSection(R.string.filter_seasons, refs.seasons, filter.seasons) { onChange(filter.copy(seasons = it), titleSorted) }
                    multiSection(R.string.filter_genres, refs.genres, filter.genres) { onChange(filter.copy(genres = it), titleSorted) }
                    item {
                        val years = remember(refs.years) { refs.years.sorted() }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.filter_years), color = Color(0xFFB4B4BC), fontSize = 16.sp)
                            Text(stringResource(R.string.filter_year_from), color = Color.LightGray, fontSize = 15.sp)
                            YearRow(years, filter.fromYear) { year ->
                                val to = filter.toYear?.let { if (year != null && it < year) year else it }
                                onChange(filter.copy(fromYear = year, toYear = to), titleSorted)
                            }
                            Text(stringResource(R.string.filter_year_to), color = Color.LightGray, fontSize = 15.sp)
                            YearRow(years, filter.toYear, down = showFocus) { year ->
                                val from = filter.fromYear?.let { if (year != null && it > year) year else it }
                                onChange(filter.copy(fromYear = from, toYear = year), titleSorted)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DialogButton(stringResource(R.string.filter_show), onDismiss, modifier = Modifier.focusRequester(showFocus))
                    DialogButton(stringResource(R.string.filter_reset), onReset)
                }
            }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    }
}

/** A filter group whose options can be combined: OK toggles one. */
private fun LazyListScope.multiSection(title: Int, options: List<FilterOption>, selected: Set<String>,
    onChange: (Set<String>) -> Unit) = item {
    PanelSection(stringResource(title)) {
        options.forEach { option ->
            val on = option.id in selected
            PanelChip(option.title, on) { onChange(if (on) selected - option.id else selected + option.id) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelSection(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFFB4B4BC), fontSize = 16.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun PanelChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AccentButton(onClick = onClick, modifier = modifier
        .then(if (selected) Modifier.border(2.dp, Color(0xFFB4B4BC), RoundedCornerShape(50)) else Modifier)) {
        // An icon, not a "✓" character: that glyph comes from a fallback font and makes the chip taller.
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(18.dp))
        Text(text, fontSize = 16.sp, maxLines = 1)
    }
}
