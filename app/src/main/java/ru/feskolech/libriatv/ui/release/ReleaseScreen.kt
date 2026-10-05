package ru.feskolech.libriatv.ui.release

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
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import ru.feskolech.libriatv.R
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
            LaunchedEffect(release.id) { focus.requestFocus(); kotlinx.coroutines.delay(100); listState.scrollToItem(0) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(Color(0xFF101010)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp, vertical = 27.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        AsyncImage(release.posterUrl, null, Modifier.width(185.dp).height(278.dp), contentScale = ContentScale.Crop)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(release.title, style = androidx.tv.material3.MaterialTheme.typography.headlineLarge, color = Color.White)
                            Text(listOfNotNull(release.year?.toString(), release.season, release.type, release.publishDay).joinToString(" • "), color = Color.White)
                            Text(release.genres.joinToString(" • "), color = Color.White)
                            // Short teaser keeps the header height stable (a long text made it jitter while moving
                            // between the buttons); the full text is one button away.
                            val description = release.description.orEmpty().replace(Regex("<[^>]*>"), "").trim()
                            Text(description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
                            // Buttons sit at the bottom of the header: bring-into-view alone stops once they are
                            // visible and leaves the title cut off when coming back up from the episodes.
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                Button(onClick = { first?.let { onPlay(it.id) } }, enabled = first != null,
                                    modifier = Modifier.focusRequester(focus)) {
                                    Text(if (resume != null) stringResource(R.string.continue_episode, resume.ordinal?.toInt() ?: 1)
                                        else stringResource(R.string.watch))
                                }
                                Button(onClick = { viewModel.toggleFavorite(onLogin) }) {
                                    Text(stringResource(if (current.favorite) R.string.remove_favorite else R.string.add_favorite))
                                }
                                Button(onClick = { onTorrents(release.id) }) { Text(stringResource(R.string.torrents)) }
                                if (description.isNotEmpty()) {
                                    Button(onClick = { showDescription = true }) { Text(stringResource(R.string.description_more)) }
                                }
                            }
                            if (showDescription) DescriptionDialog(release.title, description) { showDescription = false }
                            if (current.favoriteError != null) {
                                Text(current.favoriteError, color = Color(0xFFFF8888))
                                Button(onClick = { viewModel.toggleFavorite(onLogin) }) { Text(stringResource(R.string.retry)) }
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
