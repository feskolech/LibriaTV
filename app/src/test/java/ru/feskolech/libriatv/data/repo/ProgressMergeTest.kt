package ru.feskolech.libriatv.data.repo

import org.junit.Assert.*
import org.junit.Test

class ProgressMergeTest {
    @Test fun furthestPositionWins() {
        val local = PlaybackProgress(120_000, 300_000)
        assertEquals(120_000, mergeProgress(local, ServerTimecode("a", 90_000, false))!!.positionMs)
        assertEquals(180_000, mergeProgress(local, ServerTimecode("a", 180_000, false))!!.positionMs)
    }

    @Test fun serverWatchedWinsEvenWhenPositionIsBehind() {
        val merged = mergeProgress(PlaybackProgress(120_000, 300_000), ServerTimecode("a", 10_000, true))!!
        assertTrue(merged.watched)
        assertEquals(120_000, merged.positionMs)
        assertFalse(mergeProgress(PlaybackProgress(95_000, 100_000), ServerTimecode("a", 10_000, false))!!.watched)
    }

    @Test fun guestKeepsLocalProgress() {
        assertEquals(PlaybackProgress(30_000, 100_000), mergeProgress(PlaybackProgress(30_000, 100_000), null))
        assertNull(mergeProgress(null, null))
    }
}
