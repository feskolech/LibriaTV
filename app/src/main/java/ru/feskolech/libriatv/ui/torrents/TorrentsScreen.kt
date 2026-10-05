package ru.feskolech.libriatv.ui.torrents

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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.WideButtonScale
import ru.feskolech.libriatv.domain.Torrent
import ru.feskolech.libriatv.ui.components.makeQr

@Composable
fun TorrentsScreen(viewModel: TorrentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val qrMagnet by viewModel.qrMagnet.collectAsState()
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF101010)).padding(horizontal = 48.dp, vertical = 27.dp),
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
                if (current.torrents.isEmpty()) {
                    Text(stringResource(R.string.torrents_empty), color = Color.White)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        itemsIndexed(current.torrents, key = { _, t -> t.id }) { index, torrent ->
                            TorrentRow(
                                torrent,
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                onClick = {
                                    val magnet = torrent.magnet ?: return@TorrentRow
                                    if (!openMagnet(context, magnet)) viewModel.showQr(magnet)
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
    Dialog(onDismissRequest = onDismiss) {
        Surface {
            Row(Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
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
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}
