package ru.feskolech.libriatv.ui.schedule

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.MaterialTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.ScheduleItem
import ru.feskolech.libriatv.ui.components.DrawerBrowsing

/** Schedule tabs with the remote: it opens on today, and Up from the posters never switches the day. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleContentTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** The menu state is app-wide: an open menu left by another test would stop the tabs taking the focus. */
    @Before fun menuClosed() { DrawerBrowsing.active = false }

    private fun item(id: Int, title: String) = ScheduleItem(
        Release(id, title, null, null, null, null, emptyList(), emptyList(), null), null, null)

    /** Thursday is today; each day has its own show, so the shown posters tell which day is selected. */
    private val week = ScheduleUiState.Content(
        days = List(7) { day -> listOf(item(day + 1, "Show of day $day")) },
        today = 3,
    )

    private fun day(index: Int) = rule.activity.resources.getStringArray(R.array.week_days)[index]
    private fun press(key: Key) = rule.onNode(isFocused()).performKeyInput { pressKey(key) }

    private fun show() = rule.setContent {
        MaterialTheme { ScheduleContent(week, onRetry = {}, onOpenRelease = {}, onContentFocus = {}) }
    }

    @Test
    fun `opens on today with today's posters`() {
        show()
        rule.onNodeWithText(rule.activity.getString(R.string.schedule_today, day(3))).assertIsFocused()
        rule.onNodeWithContentDescription("Show of day 3").assertExists()
    }

    @Test
    fun `up from a poster returns to the selected day, not to the tab above it`() {
        show()
        press(Key.DirectionDown)
        // The only poster sits in the first column, right under Monday's tab.
        rule.onNodeWithContentDescription("Show of day 3").assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithText(rule.activity.getString(R.string.schedule_today, day(3))).assertIsFocused()
        rule.onNodeWithContentDescription("Show of day 3").assertExists()
        rule.onNodeWithContentDescription("Show of day 0").assertDoesNotExist()
    }

    @Test
    fun `moving along the tabs switches the day`() {
        show()
        press(Key.DirectionRight)
        rule.onNodeWithText(day(4)).assertIsFocused()
        rule.onNodeWithContentDescription("Show of day 4").assertExists()
    }
}
