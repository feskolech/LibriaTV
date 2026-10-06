package ru.feskolech.libriatv.data.repo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.feskolech.libriatv.domain.Release

class LegacyCatalogTest {
    private val sample = """{"id":10299,"code":"black-clover-2nd-season","names":["Чёрный клевер 2","Black Clover 2nd Season"],
        "poster":"/storage/releases/posters/10299/a.jpg","year":"2026","season":"осень","type":"ТВ, 24 мин.",
        "statusCode":"1","genres":["Сёнен","Экшен"],"last":"1791055624",
        "playlist":[{"id":1,"uuid":"a2e5","name":"Начало битвы","ordinal":1,"skips":{"ending":[],"opening":[254,344]},
        "poster":"/storage/p.jpg","sd":"https://x/480.m3u8","hd":"https://x/720.m3u8","fullhd":"https://x/1080.m3u8"}]}"""

    @Test fun mapsLegacyReleaseWithEpisodes() {
        val r = Json.parseToJsonElement(sample).jsonObject.toRelease(withEpisodes = true)!!
        assertEquals(10299, r.id)
        assertEquals("Чёрный клевер 2", r.title)
        assertEquals("https://anilibria.top/storage/releases/posters/10299/a.jpg", r.posterUrl)
        assertEquals("ТВ", r.type)
        assertEquals(2026, r.year)
        val e = r.episodes.single()
        assertEquals("a2e5", e.id)
        assertEquals(254.0, e.opening?.start)
        assertEquals(null, e.ending)
        assertEquals("https://x/720.m3u8", e.hls720)
    }

    @Test fun legacyOnlyTitlesJoinInLegacyOrder() {
        fun r(id: Int) = Release(id, "t$id", null, null, null, null, emptyList(), emptyList(), null)
        val merged = mergeById(listOf(r(5152), r(9518)), listOf(r(5255), r(10299)), listOf(r(5152), r(5255), r(9518), r(10299)))
        assertEquals(listOf(5152, 5255, 9518, 10299), merged.map { it.id })
        assertEquals(listOf(1, 2), mergeById(listOf(r(1), r(2)), emptyList(), emptyList()).map { it.id })
    }

    @Test fun blockedReleaseWithoutPlaylistIsMarked() {
        val blocked = Json.parseToJsonElement(
            """{"id":10299,"names":["Чёрный клевер 2"],"blockedInfo":{"blocked":true},"playlist":[]}"""
        ).jsonObject.toRelease(withEpisodes = true)!!
        assertEquals(true, blocked.regionBlocked)
        val open = Json.parseToJsonElement(sample).jsonObject.toRelease(withEpisodes = true)!!
        assertEquals(false, open.regionBlocked)
    }
}
