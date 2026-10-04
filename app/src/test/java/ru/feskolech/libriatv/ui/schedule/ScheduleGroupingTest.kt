package ru.feskolech.libriatv.ui.schedule

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.ScheduleItem

class ScheduleGroupingTest {
    private fun item(id: Int, day: Int?, title: String = "r$id") = ScheduleItem(
        Release(id, title, null, null, null, null, emptyList(), emptyList(), null, publishDayNumber = day), null, null,
    )

    @Test fun groupsMondayFirstAndSortsByTitle() {
        val days = ScheduleViewModel.groupByDay(listOf(item(1, 7, "Б"), item(2, 1), item(3, 7, "А"), item(4, null)))
        assertEquals(7, days.size)
        assertEquals(listOf(2), days[0].map { it.release.id })
        assertEquals(listOf(3, 1), days[6].map { it.release.id })
        assertEquals(3, days.sumOf { it.size }) // unknown day is dropped
    }

    @Test fun todayIndexStartsOnMonday() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); assertEquals(0, ScheduleViewModel.todayIndex(cal))
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY); assertEquals(6, ScheduleViewModel.todayIndex(cal))
    }
}
