package ru.feskolech.libriatv.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.feskolech.libriatv.data.repo.NewFavoriteEpisode
import ru.feskolech.libriatv.domain.Release

class NewEpisodesMergeTest {
    private fun release(id: Int) = Release(id, "r$id", null, null, null, null, emptyList(), emptyList(), null)

    @Test fun aLaterCheckExtendsTheNoticeStillShown() {
        val shown = listOf(NewFavoriteEpisode(release(1), 6, 6))
        val fresh = listOf(NewFavoriteEpisode(release(1), 7, 7), NewFavoriteEpisode(release(2), 3, 3))
        // Release 1 now reads "episodes 6–7", release 2 is added after it.
        assertEquals(listOf(NewFavoriteEpisode(release(1), 6, 7), NewFavoriteEpisode(release(2), 3, 3)),
            mergeNewEpisodes(shown, fresh))
    }

    @Test fun nothingShownKeepsTheFreshList() {
        val fresh = listOf(NewFavoriteEpisode(release(1), 2, 4))
        assertEquals(fresh, mergeNewEpisodes(emptyList(), fresh))
    }
}
