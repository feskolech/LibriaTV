package ru.feskolech.libriatv.ui.player

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.feskolech.libriatv.ui.components.LoadingSpinner

/** Spinner shown while the player buffers, so a slow 1080p start is not just a black screen. */
@Composable
fun BufferingIndicator(modifier: Modifier = Modifier) = LoadingSpinner(modifier)

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
