package ru.feskolech.libriatv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.Skip

class PlaybackRulesTest {
    private fun episode(id: String, ordinal: Double?, opening: Skip? = null, ending: Skip? = null,
        sd: String? = "sd", hd: String? = "hd", fhd: String? = "fhd") =
        Episode(id, "", ordinal, opening, ending, null, sd, hd, fhd)

    // Deliberately out of order: the API does not promise a sorted list.
    private val release = Release(1, "t", null, null, null, null, emptyList(),
        episodes = listOf(episode("e3", 3.0), episode("e1", 1.0), episode("e2", 2.0)), latestEpisode = null)

    @Test
    fun `episodes are walked in number order`() {
        assertEquals(listOf("e1", "e2", "e3"), release.orderedEpisodes().map { it.id })
        assertEquals("e2", release.episodeAfter("e1")?.id)
        assertEquals("e2", release.episodeBefore("e3")?.id)
        assertNull(release.episodeAfter("e3"))
        assertNull(release.episodeBefore("e1"))
        assertNull(release.episodeAfter("missing"))
        assertEquals("e3", release.episodeNumber(3)?.id)
        assertNull(release.episodeNumber(9))
    }

    @Test
    fun `only the highest-numbered episode ends the release`() {
        assertTrue(release.isLastEpisode("e3"))
        assertFalse(release.isLastEpisode("e2"))
        assertFalse(release.isLastEpisode("e1"))
    }

    @Test
    fun `the preferred stream wins, otherwise the best available one`() {
        assertEquals(720 to "hd", streamFor(episode("e", 1.0), 720))
        assertEquals(720 to "hd", streamFor(episode("e", 1.0, fhd = null), 1080))
        assertEquals(480 to "sd", streamFor(episode("e", 1.0, hd = " ", fhd = null), 1080))
        assertNull(streamFor(episode("e", 1.0, sd = null, hd = null, fhd = null), 1080))
    }

    @Test
    fun `lower quality is offered only when that stream exists`() {
        assertEquals(720, lowerQuality(episode("e", 1.0), 1080))
        assertEquals(480, lowerQuality(episode("e", 1.0), 720))
        assertNull(lowerQuality(episode("e", 1.0), 480))
        assertNull(lowerQuality(episode("e", 1.0, hd = null), 1080))
    }

    @Test
    fun `skip segments cover start inclusive and stop exclusive`() {
        val e = episode("e", 1.0, opening = Skip(10.0, 100.0), ending = Skip(1300.0, 1390.0))
        assertNull(activeSkip(e, 9_999))
        assertEquals(ActiveSkip(Skip(10.0, 100.0), isOpening = true), activeSkip(e, 10_000))
        assertNull(activeSkip(e, 100_000))
        assertEquals(false, activeSkip(e, 1_350_000)?.isOpening)
        assertNull(activeSkip(episode("e", 1.0, opening = Skip(null, 100.0)), 50_000))
    }

    @Test
    fun `holding the seek key speeds up`() {
        assertEquals(listOf(10, 10, 10, 30, 30, 60), listOf(0, 1, 2, 3, 7, 8).map(::seekStepSeconds))
    }

    @Test
    fun `only timed sleep timers have a deadline`() {
        assertEquals(1_000 + 15 * 60_000L, sleepDeadline(SleepTimer.Minutes15, 1_000))
        assertEquals(60 * 60_000L, sleepDeadline(SleepTimer.Minutes60, 0))
        assertEquals(0L, sleepDeadline(SleepTimer.AfterEpisode, 1_000))
        assertEquals(0L, sleepDeadline(SleepTimer.Off, 1_000))
    }

    @Test
    fun `next episode countdown runs in the last 8 seconds when it will auto-start`() {
        assertEquals(Long.MAX_VALUE, remainingSeconds(5_000, 0))
        assertEquals(5L, remainingSeconds(95_000, 100_000))
        assertEquals(5, nextCountdown(autoNext = true, SleepTimer.Off, remaining = 5, hasNext = true))
        assertNull(nextCountdown(autoNext = true, SleepTimer.Off, remaining = 9, hasNext = true))
        assertNull(nextCountdown(autoNext = true, SleepTimer.Off, remaining = 0, hasNext = true))
        assertNull(nextCountdown(autoNext = false, SleepTimer.Off, remaining = 5, hasNext = true))
        assertNull(nextCountdown(autoNext = true, SleepTimer.AfterEpisode, remaining = 5, hasNext = true))
        assertNull(nextCountdown(autoNext = true, SleepTimer.Off, remaining = 5, hasNext = false))
    }

    @Test
    fun `a watched episode starts over, an unfinished one resumes`() {
        assertEquals(0L, resumePositionMs(null, null))
        assertEquals(600_000L, resumePositionMs(600_000, 1_500_000))
        assertEquals(0L, resumePositionMs(1_490_000, 1_500_000))
        assertEquals(600_000L, resumePositionMs(600_000, 0))
    }

    @Test
    fun `an episode counts as watched from 90 percent`() {
        assertTrue(isWatched(90_000, 100_000))
        assertFalse(isWatched(89_999, 100_000))
        assertFalse(isWatched(10, 0))
    }
}
