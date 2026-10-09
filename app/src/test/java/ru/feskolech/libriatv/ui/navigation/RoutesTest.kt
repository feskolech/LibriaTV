package ru.feskolech.libriatv.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutesTest {
    @Test
    fun `deep links to an episode and a release become routes`() {
        assertEquals("player/10299/ab-12_x", Routes.fromDeepLink("libriatv", "play", listOf("10299", "ab-12_x")))
        assertEquals("release/10299", Routes.fromDeepLink("libriatv", "release", listOf("10299")))
    }

    @Test
    fun `malformed or foreign deep links are ignored`() {
        assertNull(Routes.fromDeepLink("https", "release", listOf("1")))
        assertNull(Routes.fromDeepLink("libriatv", "release", listOf("abc")))
        assertNull(Routes.fromDeepLink("libriatv", "release", emptyList()))
        assertNull(Routes.fromDeepLink("libriatv", "play", listOf("1")))
        assertNull(Routes.fromDeepLink("libriatv", "play", listOf("1", "a/b")))
        assertNull(Routes.fromDeepLink("libriatv", "play", listOf("1", "x".repeat(81))))
        assertNull(Routes.fromDeepLink("libriatv", "other", listOf("1")))
    }

    @Test
    fun `builders produce the registered patterns`() {
        assertEquals("release/5", Routes.release(5))
        assertEquals("torrents/5", Routes.torrents(5))
        assertEquals("player/5/e1", Routes.player(5, "e1"))
    }

    @Test
    fun `pages belong to no section, a search with a query belongs to Search`() {
        assertEquals(Destination.Catalog, Routes.section("catalog"))
        assertEquals(Destination.Search, Routes.section(Routes.SEARCH_QUERY))
        assertNull(Routes.section(Routes.RELEASE))
        assertNull(Routes.section(Routes.PLAYER))
    }
}
