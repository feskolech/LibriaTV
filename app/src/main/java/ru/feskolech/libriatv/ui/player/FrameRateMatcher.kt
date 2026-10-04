package ru.feskolech.libriatv.ui.player

import kotlin.math.abs
import kotlin.math.roundToInt

/** A display mode reduced to what refresh-rate matching needs (keeps the logic unit-testable). */
data class DisplayModeInfo(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

object FrameRateMatcher {
    /**
     * Picks the mode with the current resolution whose refresh rate is an integer multiple of the
     * video frame rate (23.976 → 23.976/24/48, 25 → 50, 29.97 → 59.94/60), preferring the lowest
     * multiple and an exact NTSC match. Returns null when nothing fits or the current mode already does.
     */
    fun bestMode(frameRate: Float, current: DisplayModeInfo, modes: List<DisplayModeInfo>): DisplayModeInfo? {
        if (frameRate <= 0f) return null
        if (matches(frameRate, current.refreshRate)) return null
        return modes
            .filter { it.width == current.width && it.height == current.height && matches(frameRate, it.refreshRate) }
            .minWithOrNull(compareBy<DisplayModeInfo>({ multiple(frameRate, it.refreshRate) }, { abs(it.refreshRate / multiple(frameRate, it.refreshRate) - frameRate) }))
    }

    private fun multiple(frameRate: Float, refreshRate: Float): Int = (refreshRate / frameRate).roundToInt().coerceAtLeast(1)

    /** Within 0.2 %: 24.000 vs 23.976 differs by 0.1 % — both acceptable for 24p content. */
    private fun matches(frameRate: Float, refreshRate: Float): Boolean {
        val n = multiple(frameRate, refreshRate)
        return abs(refreshRate - n * frameRate) / refreshRate < 0.002f
    }
}
