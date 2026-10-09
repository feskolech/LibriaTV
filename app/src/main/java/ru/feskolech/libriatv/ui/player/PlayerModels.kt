package ru.feskolech.libriatv.ui.player

import android.graphics.Bitmap
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.Skip

enum class PlayerPanel { Hidden, Controls, Episodes, Settings }
enum class SleepTimer { Off, Minutes15, Minutes30, Minutes60, AfterEpisode }

data class PlayerContent(
    val release: Release, val episode: Episode, val quality: Int,
    val positionMs: Long = 0, val durationMs: Long = 0, val playing: Boolean = false,
    val panel: PlayerPanel = PlayerPanel.Hidden, val skip: Skip? = null,
    val skipOpening: Boolean = false, val nextCountdown: Int? = null,
    val autoSkipOpening: Boolean = false, val autoSkipEnding: Boolean = false, val error: String? = null,
    val buffering: Boolean = true, val speed: Float = 1f,
    /** Frame rate of the playing video, or null if the stream does not declare it. */
    val frameRate: Float? = null, val frameRateMatch: Boolean = true, val nightMode: Boolean = false,
    val autoNext: Boolean = true,
    val sleepTimer: SleepTimer = SleepTimer.Off, val sleepWarning: Boolean = false,
    val seekTargetMs: Long? = null, val seekFrame: Bitmap? = null,
    val qualityHint: Int? = null,
    /**
     * The video should be playing (also while it buffers or retries), so the TV must not dim or sleep.
     * Off when paused (by the viewer or the sleep timer) and after the last episode ends.
     */
    val keepAwake: Boolean = false,
    /** The last episode of the release has played to the end: the screen leaves the player. */
    val finished: Boolean = false,
)

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Content(val value: PlayerContent) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}
