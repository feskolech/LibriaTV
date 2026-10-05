package ru.feskolech.libriatv.ui.player
import androidx.tv.material3.MaterialTheme

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.UiSounds
import ru.feskolech.libriatv.remote.RemoteCommand
import kotlinx.coroutines.flow.Flow

@Composable
fun PlayerScreen(onBack: () -> Unit, remoteCommands: Flow<RemoteCommand>, viewModel: PlayerViewModel = hiltViewModel()) {
    LaunchedEffect(remoteCommands) {
        remoteCommands.collect { command ->
            when (command) {
                RemoteCommand.Pause -> viewModel.togglePause()
                RemoteCommand.SeekBack -> viewModel.seek(-1)
                RemoteCommand.SeekForward -> viewModel.seek(1)
                RemoteCommand.NextEpisode -> viewModel.nextEpisode()
                else -> Unit
            }
        }
    }
    val state by viewModel.state.collectAsState()
    androidx.compose.runtime.DisposableEffect(Unit) {
        UiSounds.enabled = false
        onDispose { UiSounds.enabled = true }
    }
    val rootFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val context = LocalContext.current
    val current = (state as? PlayerUiState.Content)?.value
    // With no panel open, focus must always sit on the skip button (if shown) or the player itself;
    // losing it leaves the remote dead, since key handlers only see events from a focused subtree.
    LaunchedEffect(current?.episode?.id, current?.panel, current?.skip != null) {
        if (current != null && current.panel == PlayerPanel.Hidden) {
            withFrameNanos { }
            runCatching { if (current.skip != null) skipFocus.requestFocus() else rootFocus.requestFocus() }
        }
    }
    // An opened panel must own the focus, otherwise the remote has nothing to move from.
    LaunchedEffect(current?.panel) {
        if (current != null && current.panel != PlayerPanel.Hidden) {
            withFrameNanos { }
            runCatching { panelFocus.requestFocus() }
        }
    }
    BackHandler {
        if (current?.qualityHint != null) viewModel.dismissQualityHint()
        else if (!viewModel.hidePanel()) viewModel.close(onBack)
    }
    FrameRateMatchEffect(current?.frameRate.takeIf { current?.frameRateMatch == true })
    // OK is resolved on key-up so that a long press can open the quick menu instead of pausing.
    val okLongPressed = remember { booleanArrayOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val hidden = current?.panel == PlayerPanel.Hidden
            val code = native.keyCode
            if (current?.sleepWarning == true && native.action == KeyEvent.ACTION_UP &&
                (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
                viewModel.cancelSleepWarning(); return@onPreviewKeyEvent true
            }
            if (current?.qualityHint != null && native.action == KeyEvent.ACTION_UP &&
                (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
                viewModel.acceptQualityHint(); return@onPreviewKeyEvent true
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (!hidden || current?.skip != null) return@onPreviewKeyEvent false
                when {
                    native.action == KeyEvent.ACTION_DOWN && native.repeatCount == 0 -> okLongPressed[0] = false
                    native.action == KeyEvent.ACTION_DOWN && !okLongPressed[0] -> {
                        okLongPressed[0] = true
                        viewModel.showPanel(PlayerPanel.Settings)
                    }
                    native.action == KeyEvent.ACTION_UP && !okLongPressed[0] -> viewModel.togglePause()
                }
                return@onPreviewKeyEvent true
            }
            if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (hidden) { viewModel.seek(-1, native.repeatCount); true } else false
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (hidden) { viewModel.seek(1, native.repeatCount); true } else false
                KeyEvent.KEYCODE_DPAD_UP -> if (hidden) { viewModel.showPanel(PlayerPanel.Controls); true } else false
                KeyEvent.KEYCODE_DPAD_DOWN -> if (hidden) { viewModel.showPanel(PlayerPanel.Episodes); true } else false
                // Bonus keys: only some remotes have them, nothing depends on them.
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { viewModel.togglePause(); true }
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> { viewModel.nextEpisode(); true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> { viewModel.previousEpisode(); true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { viewModel.seek(1, native.repeatCount); true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { viewModel.seek(-1, native.repeatCount); true }
                KeyEvent.KEYCODE_MENU -> { viewModel.showPanel(PlayerPanel.Settings); true }
                in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> { viewModel.playEpisodeNumber(code - KeyEvent.KEYCODE_0); true }
                else -> false
            }
        }) {
        if (current != null) {
            AndroidView(factory = { PlayerView(context).apply {
                useController = false
                setKeepContentOnPlayerReset(true)
                player = viewModel.player
            } },
                modifier = Modifier.fillMaxSize(), update = { it.player = viewModel.player })
            Box(Modifier.fillMaxSize().focusRequester(rootFocus).focusable())
            if (current.buffering && current.error == null) {
                BufferingIndicator(Modifier.align(Alignment.Center))
            }
            current.seekTargetMs?.let { target ->
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)
                    .background(Color(0xE6101010)).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    current.seekFrame?.let { bitmap ->
                        Image(bitmap.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.width(320.dp).height(180.dp), contentScale = ContentScale.Fit)
                    }
                    Text(formatTime(target), color = Color.White)
                }
            }
            if (current.sleepWarning) {
                Button(onClick = viewModel::cancelSleepWarning,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 27.dp)) {
                    Text(stringResource(R.string.sleep_warning))
                }
            }
            current.qualityHint?.let { quality ->
                Button(onClick = viewModel::acceptQualityHint,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 85.dp)) {
                    Text(stringResource(R.string.quality_hint, quality))
                }
            }
            if (current.skip != null) {
                Button(onClick = viewModel::skip,
                    modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 48.dp, vertical = 27.dp)
                        .focusRequester(skipFocus)) {
                    Text(stringResource(if (current.skipOpening) R.string.skip_opening else R.string.skip_ending))
                }
            }
            if (current.panel == PlayerPanel.Settings) {
                PlayerSettingsMenu(
                    content = current,
                    firstFocus = panelFocus,
                    onQuality = viewModel::changeQuality,
                    onSpeed = viewModel::setSpeed,
                    onAutoSkipOpening = viewModel::toggleAutoSkipOpening,
                    onAutoSkipEnding = viewModel::toggleAutoSkipEnding,
                    onNightMode = viewModel::toggleNightMode,
                    onSleepTimer = viewModel::setSleepTimer,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            } else if (current.panel != PlayerPanel.Hidden) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE6101010))
                    .padding(horizontal = 48.dp, vertical = 27.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(current.release.title + " • " + stringResource(R.string.episode_number, current.episode.ordinal?.toInt() ?: 0), color = Color.White)
                    Text(formatTime(current.positionMs) + " / " + formatTime(current.durationMs), color = Color.White)
                    Box(Modifier.fillMaxWidth().height(5.dp).background(Color.DarkGray)) {
                        Box(Modifier.fillMaxWidth(if (current.durationMs > 0) (current.positionMs.toFloat() / current.durationMs).coerceIn(0f, 1f) else 0f)
                            .fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    }
                    current.error?.let { error ->
                        if (error == PlayerViewModel.PLAYBACK_FAILED) {
                            Text(stringResource(R.string.player_network_error), color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = viewModel::retryPlayback, modifier = Modifier.focusRequester(panelFocus)) {
                                    Text(stringResource(R.string.retry))
                                }
                                val lower = listOf(1080, 720, 480).filter { it < current.quality }.firstOrNull { q ->
                                    when (q) { 720 -> current.episode.hls720; else -> current.episode.hls480 } != null
                                }
                                if (lower != null) Button(onClick = { viewModel.changeQuality(lower) }) {
                                    Text(stringResource(R.string.player_switch_quality, lower))
                                }
                            }
                        } else Text(error, color = Color.White)
                    }
                    if (current.panel == PlayerPanel.Controls) {
                        // Playback only; everything that is a setting lives behind the gear.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ControlButton(Icons.Filled.SkipPrevious, R.string.previous_episode, viewModel::previousEpisode)
                            ControlButton(Icons.Filled.Replay10, R.string.seek_back, { viewModel.seek(-1) })
                            ControlButton(if (current.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (current.playing) R.string.pause else R.string.play, viewModel::togglePause,
                                if (current.error == null) Modifier.focusRequester(panelFocus) else Modifier)
                            ControlButton(Icons.Filled.Forward10, R.string.seek_forward, { viewModel.seek(1) })
                            ControlButton(Icons.Filled.SkipNext, R.string.next_episode, viewModel::nextEpisode)
                            Spacer(Modifier.weight(1f))
                            ControlButton(Icons.AutoMirrored.Filled.List, R.string.episodes, { viewModel.showPanel(PlayerPanel.Episodes) })
                            ControlButton(Icons.Filled.Settings, R.string.player_settings, { viewModel.showPanel(PlayerPanel.Settings) })
                        }
                    } else {
                        val ordered = current.release.episodes.sortedBy { it.ordinal ?: 0.0 }
                        // Start scrolled to the playing episode so it is composed and can take focus.
                        val episodesState = androidx.compose.foundation.lazy.rememberLazyListState(
                            initialFirstVisibleItemIndex = (ordered.indexOfFirst { it.id == current.episode.id } - 2).coerceAtLeast(0),
                        )
                        LazyRow(state = episodesState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(ordered, key = { it.id }) { episode ->
                                Button(
                                    onClick = { viewModel.playEpisode(episode.id) },
                                    modifier = if (episode.id == current.episode.id) Modifier.focusRequester(panelFocus) else Modifier,
                                ) {
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

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "${"%.2f".format(java.util.Locale.US, speed).trimEnd('0')}×"

private fun formatTime(ms: Long): String {
    val seconds = ms / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
