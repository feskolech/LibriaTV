package ru.feskolech.libriatv.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {
    @Test fun newestFirstWithoutDuplicates() {
        val history = SearchHistoryStore.push(listOf("блич", "наруто"), "Наруто")
        assertEquals(listOf("Наруто", "блич"), history)
    }

    @Test fun keepsOnlyLimit() {
        val full = (1..SearchHistoryStore.LIMIT).map { "q$it" }
        val history = SearchHistoryStore.push(full, "new")
        assertEquals(SearchHistoryStore.LIMIT, history.size)
        assertEquals("new", history.first())
        assertEquals("q${SearchHistoryStore.LIMIT - 1}", history.last())
    }
}
