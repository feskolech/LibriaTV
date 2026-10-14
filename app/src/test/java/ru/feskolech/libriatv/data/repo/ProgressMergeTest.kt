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

    private fun item(id: Int) = ContinueItem(
        ru.feskolech.libriatv.domain.Release(id, "r$id", null, null, null, null, emptyList(), emptyList(), null),
        ru.feskolech.libriatv.domain.Episode("e$id", "", 1.0, null, null, null, "sd", null, null),
        PlaybackProgress(10_000, 100_000))

    @Test fun continueWatchingStartsWithTheReleasePlayedLast() {
        val items = listOf(item(1), item(2), item(3), item(4))
        // 3 played last, then 1; 2 and 4 only on another device: they follow in their own order.
        val ordered = orderByLastPlayed(items, mapOf(1 to 1_000L, 3 to 5_000L))
        assertEquals(listOf(3, 1, 2, 4), ordered.map { it.release.id })
    }
}
