package ru.feskolech.libriatv.ui.torrents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentFormattingTest {
    @Test fun formatsSizes() {
        assertNull(formatSize(null))
        assertNull(formatSize(0))
        val ru = java.util.Locale("ru")
        assertEquals("512 Б", formatSize(512, ru))
        assertEquals("792.8 МБ", formatSize(831_301_503, ru))
        assertEquals("8.6 ГБ", formatSize(9_199_595_073, ru))
        assertEquals("8.6 GB", formatSize(9_199_595_073, java.util.Locale.ENGLISH))
    }

    @Test fun shortensMagnetToInfoHash() {
        val full = "magnet:?xt=urn:btih:944d2e5377e6925092353a8a01cf8f619b67b255&tr=http%3A%2F%2Ftracker&dn=Hunter"
        assertEquals("magnet:?xt=urn:btih:944d2e5377e6925092353a8a01cf8f619b67b255", shortMagnet(full))
        assertEquals("not-a-magnet", shortMagnet("not-a-magnet"))
    }
}
