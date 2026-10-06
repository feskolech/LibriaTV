package ru.feskolech.libriatv.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    @Test fun matchesAllWordsIgnoringCaseYoAndPunctuation() {
        assertTrue(matchesQuery("чёрный клевер", listOf("Чёрный Клевер (Спэшл)")))
        assertTrue(matchesQuery("черный клевер", listOf("Чёрный клевер: Меч короля магов")))
        assertTrue(matchesQuery("черн клев", listOf("Чёрный клевер 2")))
        assertTrue(matchesQuery("black clover", listOf("Чёрный Клевер", "Black Clover")))
    }

    @Test fun looseMatchesAreSeparated() {
        assertFalse(matchesQuery("чёрный клевер", listOf("Чёрный призыватель", "Kuro no Shoukanshi")))
        assertFalse(matchesQuery("чёрный клевер", listOf("Волчица и черный принц", null)))
        assertFalse(matchesQuery("  ", listOf("Чёрный Клевер")))
    }
}
