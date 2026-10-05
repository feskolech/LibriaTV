package ru.feskolech.libriatv.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release

class FavoriteEpisodeCheckTest {
    private fun release(id: Int, ordinal: Int?) = Release(
        id, "Релиз $id", null, null, null, null, emptyList(), emptyList(),
        ordinal?.let { Episode("episode-$id", "", it.toDouble(), null, null, null, null, null, null) },
    )

    @Test fun firstLaunchOnlyEstablishesBaseline() {
        assertTrue(compareFavoriteEpisodes(null, listOf(release(1, 7))).isEmpty())
    }

    @Test fun newEpisodeIsReportedOncePerRelease() {
        assertEquals(listOf(NewFavoriteEpisode(release(1, 7), 7, 7)),
            compareFavoriteEpisodes(mapOf(1 to 6), listOf(release(1, 7))))
    }

    @Test fun newlyFavoritedAndRemovedReleasesDoNotNotify() {
        assertTrue(compareFavoriteEpisodes(mapOf(1 to 4), listOf(release(2, 8))).isEmpty())
    }

    @Test fun multipleEpisodesProduceRangeAndMissingOrdinalsAreIgnored() {
        assertEquals(listOf(NewFavoriteEpisode(release(1, 7), 5, 7)),
            compareFavoriteEpisodes(mapOf(1 to 4, 2 to 3), listOf(release(1, 7), release(2, null))))
    }

    @Test fun unchangedOrLowerOrdinalDoesNotNotify() {
        assertTrue(compareFavoriteEpisodes(mapOf(1 to 7, 2 to 8),
            listOf(release(1, 7), release(2, 6))).isEmpty())
    }

    @Test fun favoriteReleaseUsesEpisodesWhenLatestEpisodeIsAbsent() {
        val favorite = release(1, null).copy(episodes = listOf(
            release(1, 5).latestEpisode!!, release(1, 7).latestEpisode!!,
            release(1, 6).latestEpisode!!,
        ))
        assertEquals(7, favorite.latestFavoriteOrdinal())
        assertEquals(listOf(NewFavoriteEpisode(favorite, 5, 7)),
            compareFavoriteEpisodes(mapOf(1 to 4), listOf(favorite)))
    }
}
