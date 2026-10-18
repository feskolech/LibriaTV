package ru.feskolech.libriatv.ui.player
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
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
    // The controls / episode strip hide by themselves after 5 s without a key press while the video
    // plays (a paused video keeps them, so the viewer sees where they stopped).
    var lastKeyAt by remember { mutableStateOf(0L) }
    LaunchedEffect(current?.panel, current?.playing, lastKeyAt) {
        if (current != null && current.playing &&
            (current.panel == PlayerPanel.Controls || current.panel == PlayerPanel.Episodes)) {
            kotlinx.coroutines.delay(5_000)
            viewModel.showPanel(PlayerPanel.Hidden)
        }
    }
    // The release has no more episodes: leave the player as Back does (progress saved, marked watched).
    LaunchedEffect(current?.finished) { if (current?.finished == true) viewModel.close(onBack) }
    BackHandler {
        if (current?.qualityHint != null) viewModel.dismissQualityHint()
        else if (!viewModel.hidePanel()) viewModel.close(onBack)
    }
    // While the video plays the box must not go to sleep (it counts only remote presses as activity);
    // when paused or finished the normal screen timeout applies again.
    val view = androidx.compose.ui.platform.LocalView.current
    val keepAwake = current?.keepAwake == true
    androidx.compose.runtime.DisposableEffect(view, keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }
    // Home button / another app on top: stop playback instead of playing on in the background.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.pauseAndSave()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    FrameRateMatchEffect(current?.frameRate.takeIf { current?.frameRateMatch == true })
    // OK is resolved on key-up so that a long press can open the quick menu instead of pausing.
    val okLongPressed = remember { booleanArrayOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.action == KeyEvent.ACTION_DOWN) lastKeyAt = native.eventTime
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
                KeyEvent.KEYCODE_DPAD_UP -> when (current?.panel) {
                    // In the episode strip Up moves to its range chips first; the strip leaves by itself.
                    PlayerPanel.Hidden -> { viewModel.showPanel(PlayerPanel.Controls); true }
                    else -> false
                }
                // Any arrow up/down on the bare video brings the playback controls; episodes are one button away.
                KeyEvent.KEYCODE_DPAD_DOWN -> if (hidden) { viewModel.showPanel(PlayerPanel.Controls); true } else false
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
                Column(Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (current.panel == PlayerPanel.Hidden) 110.dp else 170.dp)
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
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .focusProperties { onExit = { if (requestedFocusDirection != androidx.compose.ui.focus.FocusDirection.Exit) cancelFocusChange() } }
                        .focusGroup(),
                )
            } else if (current.panel == PlayerPanel.Episodes) {
                // The episode strip takes the bottom of the screen, over a gradient so the video still shows.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .focusProperties {
                        onExit = {
                            if (requestedFocusDirection != androidx.compose.ui.focus.FocusDirection.Exit) cancelFocusChange()
                        }
                    }
                    .focusGroup()
                    // Dark from just above the first line down, so the hint stays readable over a bright frame.
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Transparent, 0.14f to Color(0xE6101010), 1f to Color(0xF5101010)))
                    .padding(start = 48.dp, end = 48.dp, top = 64.dp, bottom = 27.dp)) {
                    PlayerEpisodeStrip(current, panelFocus, onPlay = { viewModel.playEpisode(it) },
                        onUp = { viewModel.showPanel(PlayerPanel.Controls) })
                }
            } else if (current.panel != PlayerPanel.Hidden) {
                // A see-through HUD (like ivi): the frame is only shaded at the top, under the title, and at
                // the bottom, under the controls; the middle stays clear.
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color(0x99000000), 0.2f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0xD9000000))))
                Column(Modifier.align(Alignment.TopStart).padding(horizontal = 48.dp, vertical = 27.dp)) {
                    Text(current.release.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(stringResource(R.string.episode_number, current.episode.ordinal?.toInt() ?: 0), current.episode.name)
                        .filter { it.isNotBlank() }.joinToString(" · "), color = Color(0xFFC9C9CF), fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Focus never leaves the panel: at its edges (Left of the first button, Up from the buttons…)
                // it stays on the last element instead of vanishing behind the video.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .focusProperties {
                        onExit = {
                            if (requestedFocusDirection != androidx.compose.ui.focus.FocusDirection.Exit) cancelFocusChange()
                        }
                    }
                    .focusGroup()
                    .padding(start = 48.dp, end = 48.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    current.nextCountdown?.let { Text(stringResource(R.string.next_in, it), color = Color.White) }
                    // Playback on the left; quality, speed, the sleep timer and the rest live behind "More".
                    // A fixed-height slot: the larger, focused play button fits without moving the row.
                    Row(Modifier.fillMaxWidth().height(HUD_ROW_HEIGHT), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ControlButton(Icons.Filled.SkipPrevious, R.string.previous_episode, { viewModel.previousEpisode(keepPanel = true) })
                        ControlButton(Icons.Filled.Replay10, R.string.seek_back, { viewModel.seek(-1, keepPanel = true) })
                        ControlButton(if (current.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (current.playing) R.string.pause else R.string.play, viewModel::togglePause,
                            if (current.error == null) Modifier.focusRequester(panelFocus) else Modifier, main = true)
                        ControlButton(Icons.Filled.Forward10, R.string.seek_forward, { viewModel.seek(1, keepPanel = true) })
                        ControlButton(Icons.Filled.SkipNext, R.string.next_episode, { viewModel.nextEpisode(keepPanel = true) })
                        Spacer(Modifier.weight(1f))
                        MoreButton { viewModel.showPanel(PlayerPanel.Settings) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(formatTime(current.seekTargetMs ?: current.positionMs), color = Color.White, fontSize = 16.sp)
                        Box(Modifier.weight(1f)) {
                            SeekBar(
                                positionMs = current.seekTargetMs ?: current.positionMs,
                                durationMs = current.durationMs,
                                // Selectable only with the controls shown; Left/Right on it rewind / fast-forward,
                                // Down opens the episode strip (the hint below says so).
                                selectable = current.panel == PlayerPanel.Controls,
                                onSeek = { direction, repeat -> viewModel.seek(direction, repeat, keepPanel = true) },
                                onDown = { viewModel.showPanel(PlayerPanel.Episodes) },
                            )
                        }
                        Text(formatTime(current.durationMs), color = Color.White, fontSize = 16.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.player_episodes_hint), color = Color(0xFFC9C9CF), fontSize = 15.sp)
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFC9C9CF),
                            modifier = Modifier.padding(start = 4.dp).size(20.dp))
                    }
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

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, selectable: Boolean, onSeek: (Int, Int) -> Unit, onDown: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxWidth().height(24.dp)
            .then(if (!selectable) Modifier else Modifier
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { event ->
                    val direction = when (event.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                    if (direction != 0 && event.type == KeyEventType.KeyDown) {
                        onSeek(direction, event.nativeKeyEvent.repeatCount); true
                    } else if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                        onDown(); true
                    } else false
                }
                .focusable()),
        contentAlignment = Alignment.CenterStart,
    ) {
        val track = if (focused) 10.dp else 5.dp
        Box(Modifier.fillMaxWidth().height(track).background(Color(0x47FFFFFF), RoundedCornerShape(track / 2))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(accent, RoundedCornerShape(track / 2)))
        }
        if (focused) {
            Box(Modifier.fillMaxWidth(fraction), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(22.dp).background(Color.White, CircleShape))
            }
        }
    }
}

/** Height of the controls / episodes slot: a focused (scaled-up) icon button fits without clipping. */
private val HUD_ROW_HEIGHT = 60.dp
