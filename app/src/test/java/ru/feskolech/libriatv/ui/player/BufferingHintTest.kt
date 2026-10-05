package ru.feskolech.libriatv.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferingHintTest {
    @Test fun suggestsAfterThreeLongStallsInTwoMinutes() {
        val hint = BufferingHint()
        repeat(2) { index ->
            val start = index * 10_000L
            hint.start(start)
            assertFalse(hint.finish(start + 2_001, 1080, true))
        }
        hint.start(20_000)
        assertTrue(hint.finish(22_001, 1080, true))
        hint.dismiss()
        hint.start(30_000)
        assertFalse(hint.finish(32_001, 1080, true))
    }

    @Test fun oldAndShortStallsDoNotCount() {
        val hint = BufferingHint()
        hint.start(0)
        assertFalse(hint.finish(2_000, 1080, true))
        hint.start(3_000)
        assertFalse(hint.finish(6_000, 1080, true))
        hint.start(130_000)
        assertFalse(hint.finish(133_000, 1080, true))
        hint.start(140_000)
        assertFalse(hint.finish(143_000, 480, false))
    }
}
