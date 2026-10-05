package ru.feskolech.libriatv.ui.home

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import ru.feskolech.libriatv.domain.Release

@Composable
internal fun HomeBackdrop(release: Release?, videoEnabled: Boolean) {
    val episode = release?.latestEpisode ?: release?.episodes?.maxByOrNull { it.ordinal ?: -1.0 }
    val frame = episode?.previewUrl
    val stream = episode?.hls480
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val player = remember(context) { ExoPlayer.Builder(context).build().apply { volume = 0f } }
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) foreground = true
            if (event == Lifecycle.Event.ON_STOP) {
                foreground = false
                player.stop()
                Log.i("HomePreview", "stopped: background")
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
            Log.i("HomePreview", "released: screen left")
        }
    }
    LaunchedEffect(release?.id, stream, videoEnabled, foreground) {
        player.stop()
        player.clearMediaItems()
        ready = false
        Log.i("HomePreview", "stopped: focus or route changed")
        if (frame == null || stream.isNullOrBlank() || !videoEnabled || !foreground) return@LaunchedEffect
        delay(3_000)
        player.setMediaItem(MediaItem.fromUri(stream))
        player.prepare()
        while (player.playbackState != Player.STATE_READY && player.playerError == null) delay(100)
        if (player.playerError != null) return@LaunchedEffect
        val start = (player.duration.takeIf { it > 0 } ?: 0L) * 3 / 10
        player.seekTo(start)
        player.playWhenReady = true
        ready = true
        Log.i("HomePreview", "playing: release=${release?.id}")
        while (true) {
            delay(500)
            if (player.currentPosition >= start + 20_000 || player.playbackState == Player.STATE_ENDED) player.seekTo(start)
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Crossfade between releases instead of a hard cut (users found the switch too abrupt).
        androidx.compose.animation.Crossfade(
            targetState = frame to release?.posterUrl,
            animationSpec = androidx.compose.animation.core.tween(600),
            label = "backdrop",
        ) { (shownFrame, poster) ->
            Box(Modifier.fillMaxSize()) {
                if (shownFrame == null) {
                    AsyncImage(poster, null, Modifier.fillMaxSize().graphicsLayer {
                        if (Build.VERSION.SDK_INT >= 31) {
                            renderEffect = RenderEffect.createBlurEffect(34f, 34f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                        }
                        alpha = .36f
                    }, contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color(0xB5101010)))
                } else {
                    Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(.66f).aspectRatio(16f / 9f)) {
                        AsyncImage(shownFrame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        if (frame != null) {
            androidx.compose.animation.AnimatedVisibility(
                visible = ready,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(800)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)),
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(.66f).aspectRatio(16f / 9f),
            ) {
                AndroidView(factory = { PlayerView(it).apply {
                    useController = false
                    this.player = player
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                } }, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                Color(0xFF101010), Color(0xEB101010), Color.Transparent))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Transparent, Color(0x88101010), Color(0xFF101010)))))
        }
    }
}
