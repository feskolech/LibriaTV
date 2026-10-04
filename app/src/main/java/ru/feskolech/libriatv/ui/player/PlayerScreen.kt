package ru.feskolech.libriatv.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R

@Composable
fun PlayerScreen(onBack: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val rootFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val context = LocalContext.current
    val current = (state as? PlayerUiState.Content)?.value
    LaunchedEffect(current?.episode?.id, current?.panel, current?.skip) {
        if (current != null && current.panel == PlayerPanel.Hidden && current.skip == null) rootFocus.requestFocus()
    }
    LaunchedEffect(current?.skip) { if (current?.skip != null) skipFocus.requestFocus() }
    BackHandler {
        if (!viewModel.hidePanel()) viewModel.close(onBack)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            val hidden = current?.panel == PlayerPanel.Hidden
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (hidden) { viewModel.seek(-1, event.nativeKeyEvent.repeatCount); true } else false
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (hidden) { viewModel.seek(1, event.nativeKeyEvent.repeatCount); true } else false
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (hidden && current?.skip == null) { viewModel.togglePause(); true } else false
                KeyEvent.KEYCODE_DPAD_UP -> if (hidden) { viewModel.showPanel(PlayerPanel.Controls); true } else false
                KeyEvent.KEYCODE_DPAD_DOWN -> if (hidden) { viewModel.showPanel(PlayerPanel.Episodes); true } else false
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { viewModel.togglePause(); true }
                KeyEvent.KEYCODE_MEDIA_NEXT -> { viewModel.nextEpisode(); true }
                else -> false
            }
        }) {
        if (current != null) {
            AndroidView(factory = { PlayerView(context).apply { useController = false; player = viewModel.player } },
                modifier = Modifier.fillMaxSize(), update = { it.player = viewModel.player })
            Box(Modifier.fillMaxSize().focusRequester(rootFocus).focusable())
            if (current.skip != null) {
                Button(onClick = viewModel::skip,
                    modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 48.dp, vertical = 27.dp)
                        .focusRequester(skipFocus)) {
                    Text(stringResource(if (current.skipOpening) R.string.skip_opening else R.string.skip_ending))
                }
            }
            if (current.panel != PlayerPanel.Hidden) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE6101010))
                    .padding(horizontal = 48.dp, vertical = 27.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(current.release.title + " • " + stringResource(R.string.episode_number, current.episode.ordinal?.toInt() ?: 0), color = Color.White)
                    Text(formatTime(current.positionMs) + " / " + formatTime(current.durationMs), color = Color.White)
                    Box(Modifier.fillMaxWidth().height(5.dp).background(Color.DarkGray)) {
                        Box(Modifier.fillMaxWidth(if (current.durationMs > 0) (current.positionMs.toFloat() / current.durationMs).coerceIn(0f, 1f) else 0f)
                            .fillMaxHeight().background(Color(0xFFB32121)))
                    }
                    current.error?.let { Text(it, color = Color.White) }
                    if (current.panel == PlayerPanel.Controls) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = viewModel::togglePause) { Text(stringResource(if (current.playing) R.string.pause else R.string.play)) }
                            Button(onClick = { viewModel.seek(-1) }) { Text(stringResource(R.string.seek_back)) }
                            Button(onClick = { viewModel.seek(1) }) { Text(stringResource(R.string.seek_forward)) }
                            Button(onClick = viewModel::nextEpisode) { Text(stringResource(R.string.next_episode)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(480, 720, 1080).filter { quality ->
                                when (quality) { 480 -> current.episode.hls480; 720 -> current.episode.hls720; else -> current.episode.hls1080 } != null
                            }.forEach { quality ->
                                Button(onClick = { viewModel.changeQuality(quality) }) { Text("${quality}p" + if (quality == current.quality) " ✓" else "") }
                            }
                            Button(onClick = viewModel::toggleAutoSkip) {
                                Text(stringResource(R.string.auto_skip) + if (current.autoSkip) " ✓" else "")
                            }
                            Button(onClick = { viewModel.showPanel(PlayerPanel.Episodes) }) { Text(stringResource(R.string.episodes)) }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(current.release.episodes.sortedBy { it.ordinal ?: 0.0 }, key = { it.id }) { episode ->
                                Button(onClick = { viewModel.playEpisode(episode.id) }) {
                                    Text(stringResource(R.string.episode_number, episode.ordinal?.toInt() ?: 0))
                                }
                            }
                        }
                    }
                    current.nextCountdown?.let { Text(stringResource(R.string.next_in, it), color = Color.White) }
                }
            }
        } else when (val value = state) {
            PlayerUiState.Loading -> Text(stringResource(R.string.home_loading), Modifier.padding(48.dp))
            is PlayerUiState.Error -> Column(Modifier.padding(48.dp)) {
                Text(value.message)
                Button(onClick = viewModel::load) { Text(stringResource(R.string.retry)) }
            }
            else -> Unit
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = ms / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
