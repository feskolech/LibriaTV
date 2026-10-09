package ru.feskolech.libriatv.ui.release

import ru.feskolech.libriatv.ui.components.SingleDialogButton
import ru.feskolech.libriatv.ui.components.AppDialog
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.domain.UserList
import ru.feskolech.libriatv.ui.components.WideButtonScale
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.ui.components.PosterCard

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReleaseScreen(onPlay: (String) -> Unit, onTorrents: (Int) -> Unit, onLogin: () -> Unit,
    onOpenRelease: (Int) -> Unit, viewModel: ReleaseViewModel = hiltViewModel()) {
    // The page stays in the back stack while an episode plays; refresh its progress on return.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        // On the first resume the page is still loading, so this is a no-op then.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val state by viewModel.state.collectAsState()
    val focus = remember { FocusRequester() }
    when (val current = state) {
        ReleaseUiState.Loading -> Text(stringResource(R.string.home_loading), Modifier.fillMaxSize().padding(48.dp))
        is ReleaseUiState.Error -> Column(Modifier.fillMaxSize().padding(48.dp)) {
            Text(current.message)
            Button(onClick = viewModel::refresh) { Text(stringResource(R.string.retry)) }
        }
        is ReleaseUiState.Content -> {
            val release = current.release
            val ordered = remember(release) { release.episodes.sortedBy { it.ordinal ?: 0.0 } }
            val resume = ordered.firstOrNull { current.progress[it.id]?.let { progress -> progress.positionMs > 0 && !progress.watched } == true }
            val first = resume ?: ordered.firstOrNull()
            val listState = rememberLazyListState()
            val firstSimilar = remember { FocusRequester() }
            val headerScope = rememberCoroutineScope()
            var showDescription by remember { mutableStateOf(false) }
            var showLists by remember { mutableStateOf(false) }
            var showRating by remember { mutableStateOf(false) }
            LaunchedEffect(release.id) { focus.requestFocus(); kotlinx.coroutines.delay(100); listState.scrollToItem(0) }
            // TV lists scroll the focused item to a fixed pivot on every move, so the page jumped while
            // moving between the header buttons. Here the page only scrolls when the focused element is
            // not fully visible; the poster rows inside keep the usual TV behaviour.
            val pivotSpec = LocalBringIntoViewSpec.current
            val stillSpec = StillBringIntoViewSpec
            CompositionLocalProvider(LocalBringIntoViewSpec provides stillSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().focusRestorer().background(MaterialTheme.colorScheme.background),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp, vertical = 27.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            AsyncImage(release.posterUrl, null, Modifier.width(185.dp).height(278.dp), contentScale = ContentScale.Crop)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Compact, fixed-height header: title ≤ 2 lines and all metadata on one ellipsized line,
                                // so the header always fits the screen and moving between buttons never scrolls it.
                                Text(release.title, style = androidx.tv.material3.MaterialTheme.typography.headlineMedium,
                                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text((listOfNotNull(release.year?.toString(), release.season, release.type, release.publishDay) + release.genres)
                                    .joinToString(" • "), color = Color(0xFFD8D8D8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // Short teaser keeps the header height stable (a long text made it jitter while moving
                                // between the buttons); the full text is one button away.
                                val description = remember(release) { release.description.orEmpty().replace(HTML_TAG, "").trim() }
                                // The teaser itself opens the full text (Up from the buttons): no separate button for it.
                                if (description.isNotEmpty()) androidx.tv.material3.Surface(onClick = { showDescription = true },
                                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                    scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color.Transparent,
                                        focusedContainerColor = Color(0x1AFFFFFF), contentColor = Color.White, focusedContentColor = Color.White),
                                    border = androidx.tv.material3.ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(
                                        androidx.compose.foundation.BorderStroke(2.dp, Color.White)))) {
                                    Text(description, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                // Actions sit at the bottom of the header: bring-into-view alone stops once they are
                                // visible and leaves the title cut off when coming back up from the episodes.
                                ReleaseActions(
                                    mainLabel = if (resume != null) stringResource(R.string.continue_episode, resume.ordinal?.toInt() ?: 1)
                                        else stringResource(R.string.watch),
                                    mainEnabled = first != null, onMain = { first?.let { onPlay(it.id) } },
                                    mainModifier = Modifier.focusRequester(focus),
                                    actions = listOf(
                                        ReleaseAction(ReleaseIcons.favorite(current.favorite),
                                            stringResource(if (current.favorite) R.string.in_favorites else R.string.add_favorite)) { viewModel.toggleFavorite(onLogin) },
                                        ReleaseAction(ReleaseIcons.list(current.list != null),
                                            current.list?.let { stringResource(it.label()) } ?: stringResource(R.string.list_add)) { showLists = true },
                                        ReleaseAction(ReleaseIcons.rating(current.rating != null),
                                            current.rating?.let { stringResource(R.string.rating_own, it) } ?: stringResource(R.string.rating_rate)) { showRating = true },
                                        ReleaseAction(ReleaseIcons.torrents, stringResource(R.string.torrents)) { onTorrents(release.id) },
                                    ),
                                    modifier = Modifier.padding(top = 4.dp).onFocusChanged {
                                        // Let the focus system's own bring-into-view finish first; scrolling at the
                                        // same time gets cancelled by it and the header stays cut off.
                                        if (it.hasFocus) headerScope.launch {
                                            kotlinx.coroutines.delay(250)
                                            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                                                listState.animateScrollToItem(0)
                                            }
                                        }
                                    })
                                if (showDescription) DescriptionDialog(release.title, description) { showDescription = false }
                                if (showLists) ChoiceDialog(stringResource(R.string.list_title),
                                    UserList.entries.map { stringResource(it.label()) } + stringResource(R.string.list_remove),
                                    selected = current.list?.ordinal,
                                    onPick = { i -> viewModel.setList(UserList.entries.getOrNull(i), onLogin); showLists = false },
                                    onDismiss = { showLists = false })
                                if (showRating) ChoiceDialog(stringResource(R.string.rating_title),
                                    (10 downTo 1).map { "★ $it" } + stringResource(R.string.rating_remove),
                                    selected = current.rating?.let { 10 - it },
                                    onPick = { i -> viewModel.rate(if (i < 10) 10 - i else null, onLogin); showRating = false },
                                    onDismiss = { showRating = false })
                                if (current.favoriteError != null) {
                                    Text(current.favoriteError, color = Color(0xFFFF8888))
                                    Button(onClick = { viewModel.toggleFavorite(onLogin) }) { Text(stringResource(R.string.retry)) }
                                }
                            }
                        }
                    }
                    if (current.seasons.size > 1) {
                        item { Text(stringResource(R.string.seasons), style = androidx.tv.material3.MaterialTheme.typography.headlineMedium, color = Color.White) }
                        item {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    modifier = Modifier.focusRestorer()) {
                                    items(current.seasons, key = { it.id }) { season ->
                                        val here = season.id == release.id
                                        PosterCard(season,
                                            badge = if (here) stringResource(R.string.season_current) else listOfNotNull(season.year?.toString(), season.type).joinToString(" "),
                                            onFocus = {}, onClick = { if (!here) onOpenRelease(season.id) })
                                    }
                                }
                            }
                        }
                    }
                    item { Text(stringResource(R.string.episodes), style = androidx.tv.material3.MaterialTheme.typography.headlineMedium, color = Color.White) }
                    if (release.regionBlocked) item {
                        Text(stringResource(R.string.release_region_blocked), color = Color(0xFFD8D8D8))
                    }
                    // Range chips over a grid of numbers: any episode of a long series is a few presses away,
                    // and the similar titles below stay one Down from the grid instead of hundreds of rows.
                    item { EpisodePicker(ordered, current.progress, resumeId = resume?.id, onPlay = onPlay) }
                    if (current.similar.isNotEmpty()) {
                        item { Text(stringResource(R.string.similar), style = androidx.tv.material3.MaterialTheme.typography.headlineMedium, color = Color.White) }
                        item {
                            // Room for the focused card's scale on the left; focusRestorer brings Up/Down back to the
                            // card that was selected instead of whichever one is geometrically closest.
                            CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    // First visit (coming down from the episodes) starts at the first card;
                                    // later visits return to the card that was selected.
                                    modifier = Modifier.focusRestorer(firstSimilar)) {
                                    items(current.similar, key = { it.id }) { similar ->
                                        PosterCard(similar, badge = null, onFocus = {}, onClick = { onOpenRelease(similar.id) },
                                            modifier = if (similar.id == current.similar.first().id) Modifier.focusRequester(firstSimilar) else Modifier)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DescriptionDialog(title: String, text: String, onDismiss: () -> Unit) {
    // Focus stays on Close; ▲/▼ scroll the text so long synopses are readable with a remote.
    val scroll = androidx.compose.foundation.rememberScrollState()
    val scope = rememberCoroutineScope()
    AppDialog(onDismiss = onDismiss, title = title, widthFraction = 0.75f, actions = {
        SingleDialogButton(stringResource(R.string.close), onDismiss, modifier = Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(240f) }; true }
                Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-240f) }; true }
                else -> false
            }
        })
    }) {
        Text(text, style = androidx.tv.material3.MaterialTheme.typography.bodyLarge,
            // Takes the room left between the title and Close, so Close never falls off a short screen.
            modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp).verticalScroll(scroll))
    }
}

private fun UserList.label(): Int = when (this) {
    UserList.WATCHING -> R.string.list_watching
    UserList.PLANNED -> R.string.list_planned
    UserList.WATCHED -> R.string.list_watched
    UserList.POSTPONED -> R.string.list_postponed
    UserList.ABANDONED -> R.string.list_abandoned
}

/** Simple vertical choice list for the remote; focus starts on the current value. */
@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    AppDialog(onDismiss = onDismiss, title = title, width = 420.dp) {
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options.size) { i ->
                Button(onClick = { onPick(i) }, scale = ru.feskolech.libriatv.ui.components.WideButtonScale,
                    modifier = Modifier.fillMaxWidth().then(if (i == (selected ?: 0)) Modifier.focusRequester(focus) else Modifier)) {
                    Text((if (i == selected) "✓  " else "     ") + options[i])
                }
            }
        }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { focus.requestFocus() } }
}

/**
 * Keeps the page still while the focused element sits comfortably on screen (the header buttons).
 * Once it is cut off or reaches the bottom part of the screen (a seasons row with its captions, an
 * episode near the edge) it is brought to the middle, so the whole row and its titles are visible.
 */
@OptIn(ExperimentalFoundationApi::class)
private object StillBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset >= 0f && offset + size <= containerSize * 0.8f -> 0f
        size >= containerSize -> offset
        else -> offset - (containerSize - size) / 2f
    }
}

private val HTML_TAG = Regex("<[^>]*>")
