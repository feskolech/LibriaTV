package ru.feskolech.libriatv.ui.release
import androidx.tv.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

@Composable
fun ReleaseScreen(onPlay: (String) -> Unit, onTorrents: (Int) -> Unit, onLogin: () -> Unit,
    onOpenRelease: (Int) -> Unit, viewModel: ReleaseViewModel = hiltViewModel()) {
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
            val ordered = release.episodes.sortedBy { it.ordinal ?: 0.0 }
            val resume = ordered.firstOrNull { current.progress[it.id]?.let { progress -> progress.positionMs > 0 && !progress.watched } == true }
            val first = resume ?: ordered.firstOrNull()
            val listState = rememberLazyListState()
            val headerScope = rememberCoroutineScope()
            var showDescription by remember { mutableStateOf(false) }
            var showLists by remember { mutableStateOf(false) }
            var showRating by remember { mutableStateOf(false) }
            LaunchedEffect(release.id) { focus.requestFocus(); kotlinx.coroutines.delay(100); listState.scrollToItem(0) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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
                            val description = release.description.orEmpty().replace(Regex("<[^>]*>"), "").trim()
                            Text(description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
                            // Buttons sit at the bottom of the header: bring-into-view alone stops once they are
                            // visible and leaves the title cut off when coming back up from the episodes.
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.onFocusChanged {
                                    // Let the focus system's own bring-into-view finish first; scrolling at the
                                    // same time gets cancelled by it and the header stays cut off.
                                    if (it.hasFocus) headerScope.launch {
                                        kotlinx.coroutines.delay(250)
                                        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                                            listState.animateScrollToItem(0)
                                        }
                                    }
                                }) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = { first?.let { onPlay(it.id) } }, enabled = first != null,
                                        modifier = Modifier.focusRequester(focus)) {
                                        Text(if (resume != null) stringResource(R.string.continue_episode, resume.ordinal?.toInt() ?: 1)
                                            else stringResource(R.string.watch))
                                    }
                                    Button(onClick = { viewModel.toggleFavorite(onLogin) }) {
                                        Text(stringResource(if (current.favorite) R.string.remove_favorite else R.string.add_favorite))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = { onTorrents(release.id) }) { Text(stringResource(R.string.torrents)) }
                                    if (description.isNotEmpty()) {
                                        Button(onClick = { showDescription = true }) { Text(stringResource(R.string.description_more)) }
                                    }
                                }
                            }
                            if (showDescription) DescriptionDialog(release.title, description) { showDescription = false }
                            // Second row: account lists and own rating (sign-in prompt for guests).
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { showLists = true }) {
                                    Text(current.list?.let { stringResource(it.label()) } ?: stringResource(R.string.list_add))
                                }
                                Button(onClick = { showRating = true }) {
                                    Text(current.rating?.let { stringResource(R.string.rating_own, it) } ?: stringResource(R.string.rating_rate))
                                }
                            }
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
                item { Text(stringResource(R.string.episodes), style = androidx.tv.material3.MaterialTheme.typography.headlineMedium, color = Color.White) }
                items(ordered, key = { it.id }) { episode ->
                    EpisodeRow(episode, current.progress[episode.id], onPlay)
                }
                if (current.similar.isNotEmpty()) {
                    item { Text(stringResource(R.string.similar), style = androidx.tv.material3.MaterialTheme.typography.headlineMedium, color = Color.White) }
                    item {
                        // Room for the focused card's scale on the left; focusRestorer brings Up/Down back to the
                        // card that was selected instead of whichever one is geometrically closest.
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier.focusRestorer()) {
                            items(current.similar, key = { it.id }) { similar ->
                                PosterCard(similar, badge = null, onFocus = {}, onClick = { onOpenRelease(similar.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, progress: ru.feskolech.libriatv.data.repo.PlaybackProgress?, onPlay: (String) -> Unit) {
    Button(onClick = { onPlay(episode.id) }, modifier = Modifier.fillMaxWidth(), scale = WideButtonScale) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(episode.previewUrl, null, Modifier.size(width = 110.dp, height = 64.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Column {
                Text(stringResource(R.string.episode_number, episode.ordinal?.toInt() ?: 0) + "  " + episode.name)
                if (progress != null) Text(if (progress.watched) stringResource(R.string.watched)
                    else stringResource(R.string.progress_minutes, progress.positionMs / 60000))
            }
        }
    }
}

@Composable
private fun DescriptionDialog(title: String, text: String, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.tv.material3.Surface {
            Column(Modifier.width(720.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(title, style = androidx.tv.material3.MaterialTheme.typography.headlineSmall)
                // Focus stays on Close; ▲/▼ scroll the text so long synopses are readable with a remote.
                val scroll = androidx.compose.foundation.rememberScrollState()
                val scope = rememberCoroutineScope()
                Text(text, style = androidx.tv.material3.MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.height(360.dp).verticalScroll(scroll))
                Button(onClick = onDismiss, modifier = Modifier.focusRequester(focus).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(240f) }; true }
                        Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-240f) }; true }
                        else -> false
                    }
                }) { Text(stringResource(R.string.close)) }
            }
        }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { focus.requestFocus() } }
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
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.tv.material3.Surface {
            Column(Modifier.width(420.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = androidx.tv.material3.MaterialTheme.typography.headlineSmall)
                LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(options.size) { i ->
                        Button(onClick = { onPick(i) }, scale = ru.feskolech.libriatv.ui.components.WideButtonScale,
                            modifier = Modifier.fillMaxWidth().then(if (i == (selected ?: 0)) Modifier.focusRequester(focus) else Modifier)) {
                            Text((if (i == selected) "✓  " else "     ") + options[i])
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { focus.requestFocus() } }
}
