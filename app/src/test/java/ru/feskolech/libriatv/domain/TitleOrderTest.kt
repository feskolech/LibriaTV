package ru.feskolech.libriatv.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleOrderTest {
    private fun release(id: Int, title: String) = Release(id, title, null, null, null, null,
        emptyList(), emptyList(), null)

    @Test fun titleSortMatchesAlphabetIndexGroups() {
        val sorted = listOf(release(1, "Zeta"), release(2, "Япония"), release(3, "Ёж"),
            release(4, "Европа"), release(5, "#2"), release(6, "Alpha"),
            release(7, "Жизнь")).sortedByTitle()
        assertEquals(listOf("Европа", "Ёж", "Жизнь", "Япония", "Alpha", "Zeta", "#2"),
            sorted.map { it.title })
        assertEquals(listOf("Е", "Ё", "Ж", "Я", "A", "Z", "#"), sorted.map { it.titleLetter() })
    }
}
