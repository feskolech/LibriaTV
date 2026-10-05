package ru.feskolech.libriatv.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonTitleTest {
    @Test fun stripsSeasonSuffixes() {
        val base = "Перерождение в аристократа со способностью анализа"
        assertEquals(base, seasonBaseTitle("$base 3"))
        assertEquals(base, seasonBaseTitle("$base 2"))
        assertEquals(base, seasonBaseTitle(base))
        assertEquals("Голубая шкатулка", seasonBaseTitle("Голубая шкатулка 2"))
        assertEquals("Overlord", seasonBaseTitle("Overlord IV"))
        assertEquals("Mushoku Tensei", seasonBaseTitle("Mushoku Tensei 2nd Season"))
    }

    @Test fun seasonNumbers() {
        assertEquals(1, seasonNumber("Голубая шкатулка"))
        assertEquals(2, seasonNumber("Голубая шкатулка 2"))
        assertEquals(3, seasonNumber("Перерождение в аристократа со способностью анализа 3"))
    }
}
