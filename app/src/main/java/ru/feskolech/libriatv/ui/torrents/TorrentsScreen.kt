package ru.feskolech.libriatv.ui.torrents

import ru.feskolech.libriatv.ui.components.SingleDialogButton
import ru.feskolech.libriatv.ui.components.AppDialog
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.WideButtonScale
import ru.feskolech.libriatv.domain.Torrent
import ru.feskolech.libriatv.ui.components.makeQr
import ru.feskolech.libriatv.data.repo.TorrServeListing
import ru.feskolech.libriatv.domain.Episode
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun TorrentsScreen(viewModel: TorrentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val qrMagnet by viewModel.qrMagnet.collectAsState()
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.torrents), style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text(stringResource(R.string.torrents_hint), color = Color.LightGray)
        when (val current = state) {
            TorrentsUiState.Loading -> Text(stringResource(R.string.home_loading), color = Color.White)
            is TorrentsUiState.Error -> {
                Text(current.message, color = Color.White)
                Button(onClick = viewModel::refresh, modifier = Modifier.focusRequester(firstFocus)) { Text(stringResource(R.string.retry)) }
                LaunchedEffect(Unit) { withFrameNanos { }; runCatching { firstFocus.requestFocus() } }
            }
            is TorrentsUiState.Content -> {
                if (current.busy) Text(stringResource(R.string.torrent_loading_files), color = Color.White)
                if (current.error) Text(stringResource(R.string.torrent_files_error), color = Color.White)
                if (current.hint) Text(stringResource(R.string.torrent_server_hint), color = Color.White)
                if (current.torrents.isEmpty()) {
                    Text(stringResource(R.string.torrents_empty), color = Color.White)
                } else {
                    LazyColumn(Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        itemsIndexed(current.torrents, key = { _, t -> t.id }) { index, torrent ->
                            TorrentRow(
                                torrent,
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                onClick = {
                                    val magnet = torrent.magnet ?: return@TorrentRow
                                    viewModel.openTorrent(torrent) { link ->
                                        openMagnet(context, link).also { if (!it) viewModel.showQr(link) }
                                    }
                                },
                            )
                        }
                    }
                    LaunchedEffect(current) { withFrameNanos { }; runCatching { firstFocus.requestFocus() } }
                }
            }
        }
    }

    qrMagnet?.let { magnet -> MagnetQrDialog(magnet, onDismiss = { viewModel.showQr(null) }) }
    (state as? TorrentsUiState.Content)?.let { content ->
        content.listing?.let { listing -> FileChoiceDialog(listing, content.watchedNumbers,
            onPick = { viewModel.selectFile(it) { url -> openTorrServeStream(context, url) } },
            onDismiss = viewModel::closeListing) }
        content.markChoices?.let { choices -> MarkChoiceDialog(choices, content.watched,
            onConfirm = viewModel::mark, onDismiss = { viewModel.mark(emptySet()) }) }
    }
}

@Composable
private fun FileChoiceDialog(
    listing: TorrServeListing, watched: Set<Int>,
    onPick: (ru.feskolech.libriatv.data.repo.TorrServeFile) -> Unit, onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val first = listing.files.indexOfFirst { it.episode !in watched }.coerceAtLeast(0)
    AppDialog(onDismiss = onDismiss, title = stringResource(R.string.torrent_choose_file), width = 650.dp) {
        LazyColumn(Modifier.heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(listing.files) { index, file ->
                Button(onClick = { onPick(file) }, scale = WideButtonScale,
                    modifier = Modifier.fillMaxWidth().then(if (index == first) Modifier.focusRequester(focus) else Modifier)) {
                    Text("${if (file.episode in watched) "✓  " else ""}${file.episode?.let { "$it. " }.orEmpty()}${file.name}  •  ${formatSize(file.size).orEmpty()}",
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    LaunchedEffect(listing) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}

@Composable
private fun MarkChoiceDialog(choices: List<Episode>, watched: Set<String>, onConfirm: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(choices) { mutableStateOf(emptySet<String>()) }
    val focus = remember { FocusRequester() }
    val first = choices.indexOfFirst { it.id !in watched }.coerceAtLeast(0)
    AppDialog(onDismiss = onDismiss, title = stringResource(R.string.torrent_mark_title), width = 500.dp,
        // The list takes the focus first; "Done" is one step down from it.
        actions = { SingleDialogButton(stringResource(R.string.torrent_mark_done), { onConfirm(selected) }, initialFocus = false) }) {
        LazyColumn(Modifier.heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(choices) { index, episode ->
                Button(onClick = { selected = if (episode.id in selected) selected - episode.id else selected + episode.id },
                    modifier = Modifier.fillMaxWidth().then(if (index == first) Modifier.focusRequester(focus) else Modifier), scale = WideButtonScale) {
                    Text("${if (episode.id in selected || episode.id in watched) "✓" else "○"}  ${episode.ordinal?.toInt() ?: ""} ${episode.name}")
                }
            }
        }
    }
    LaunchedEffect(choices) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}

@Composable
private fun TorrentRow(torrent: Torrent, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth(), scale = WideButtonScale) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val episodes = torrent.episodes?.let { stringResource(R.string.torrent_episodes, it) }
                Text(
                    listOfNotNull(torrent.quality, torrent.codec, torrent.type, episodes).joinToString("  •  "),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(torrent.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                formatSize(torrent.size)?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                Text(
                    stringResource(R.string.torrent_peers, torrent.seeders ?: 0, torrent.leechers ?: 0) +
                        if (torrent.isHardsub) "  •  " + stringResource(R.string.torrent_hardsub) else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MagnetQrDialog(magnet: String, onDismiss: () -> Unit) {
    val short = remember(magnet) { shortMagnet(magnet) }
    val qr = remember(short) { makeQr(short, 360) }
    val focus = remember { FocusRequester() }
    AppDialog(onDismiss = onDismiss, width = 660.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(Color.White).padding(8.dp)) {
                    Image(qr.asImageBitmap(), contentDescription = null, modifier = Modifier.size(220.dp))
                }
                Column(Modifier.size(width = 320.dp, height = 220.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.torrent_no_app_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.torrent_no_app_text), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onDismiss, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.close)) }
                }
            }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}
