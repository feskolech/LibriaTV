package ru.feskolech.libriatv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameRateMatcherTest {
    private fun mode(id: Int, hz: Float, w: Int = 1920, h: Int = 1080) = DisplayModeInfo(id, w, h, hz)
    private val box = listOf(mode(1, 60f), mode(2, 59.94f), mode(3, 50f), mode(4, 24f), mode(5, 23.976f), mode(6, 30f), mode(7, 24f, 3840, 2160))

    @Test fun filmPicksExactNtscRate() = assertEquals(5, FrameRateMatcher.bestMode(23.976f, mode(1, 60f), box)?.id)
    @Test fun pal25Picks50() = assertEquals(3, FrameRateMatcher.bestMode(25f, mode(1, 60f), box)?.id)
    @Test fun ntsc30PicksLowestMultiple() = assertEquals(6, FrameRateMatcher.bestMode(29.97f, mode(4, 24f), box.filter { it.id != 2 })?.id)
    @Test fun keepsCurrentWhenItAlreadyMatches() = assertNull(FrameRateMatcher.bestMode(29.97f, mode(2, 59.94f), box))
    @Test fun ignoresOtherResolutions() = assertNull(FrameRateMatcher.bestMode(24f, mode(1, 60f), listOf(mode(1, 60f), mode(7, 24f, 3840, 2160))))
    @Test fun unknownFrameRateDoesNothing() = assertNull(FrameRateMatcher.bestMode(-1f, mode(1, 60f), box))
}
