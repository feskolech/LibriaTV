package ru.feskolech.libriatv.ui.player

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Spinner shown while the player buffers, so a slow 1080p start is not just a black screen. */
@Composable
fun BufferingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "buffering")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle",
    )
    Canvas(modifier.size(56.dp)) {
        drawArc(Color(0x33FFFFFF), 0f, 360f, false, style = Stroke(5.dp.toPx()))
        drawArc(Color(0xFFB32121), angle, 90f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * Switches the display to a refresh rate matching [frameRate] while the player is open and restores
 * the previous mode on exit. Does nothing when the frame rate is unknown or no matching mode exists.
 */
@Composable
fun FrameRateMatchEffect(frameRate: Float?) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(frameRate) {
        val window = activity.window
        val original = window.attributes.preferredDisplayModeId
        if (frameRate != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val display = activity.windowManager.defaultDisplay
            val current = display.mode
            val target = FrameRateMatcher.bestMode(
                frameRate,
                DisplayModeInfo(current.modeId, current.physicalWidth, current.physicalHeight, current.refreshRate),
                display.supportedModes.map { DisplayModeInfo(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) },
            )
            if (target != null) {
                window.attributes = window.attributes.also { it.preferredDisplayModeId = target.id }
            }
        }
        onDispose {
            window.attributes = window.attributes.also { it.preferredDisplayModeId = original }
        }
    }
}
