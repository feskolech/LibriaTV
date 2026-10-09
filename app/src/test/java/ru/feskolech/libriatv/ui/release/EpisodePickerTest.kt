package ru.feskolech.libriatv.ui.release

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.feskolech.libriatv.data.repo.PlaybackProgress
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.ui.components.AccentButton

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
// A TV-sized screen: the grid and its preview need the width of one.
@Config(qualifiers = "w960dp-h540dp-xhdpi")
class EpisodePickerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    // With a preview frame, so the preview pane shows a picture rather than the number placeholder.
    private fun episodes(range: IntRange) = range.map { Episode("e$it", "", it.toDouble(), null, null, "frame$it", "sd", null, null) }

    @Test
    fun `a short season is one range`() {
        assertEquals(listOf("1–13"), episodeRanges(episodes(1..13)).map { it.label })
    }

    @Test
    fun `long series split by fifty, by episode number`() {
        assertEquals(listOf("1–50", "51–100", "101–150", "151–200", "201–250", "251–300", "301–350", "351–355"),
            episodeRanges(episodes(1..355)).map { it.label })
        // The API serves this one from episode 370: ranges stay aligned to fifties.
        assertEquals(listOf("370–400", "401–450", "451–500"), episodeRanges(episodes(370..500)).map { it.label })
        assertEquals(31, episodeRanges(episodes(370..500)).first().episodes.size)
    }

    private fun press(key: Key) = rule.onNode(isFocused()).performKeyInput { pressKey(key) }

    /** 355 episodes, watched to 141, continuing at 142; a stand-in button above, like the page's actions. */
    private fun show() = rule.setContent {
        MaterialTheme {
            Column {
                val top = remember { FocusRequester() }
                AccentButton(onClick = {}, modifier = Modifier.focusRequester(top)) { Text("Watch") }
                val progress = (1..142).associate { "e$it" to PlaybackProgress(if (it < 142) 1_000 else 500, 1_000) }
                EpisodePicker(episodes(1..355), progress, resumeId = "e142", onPlay = {})
                LaunchedEffect(Unit) { top.requestFocus() }
            }
        }
    }

    @Test
    fun `down from the page buttons lands on the range to continue, then on that episode`() {
        show()
        rule.onNodeWithText("Watch").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithText("101–150").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithText("142").assertIsFocused()
    }

    @Test
    fun `up from the grid returns to the selected range, not the one above`() {
        show()
        press(Key.DirectionDown); press(Key.DirectionDown)
        // 142 is in the grid's bottom row: four rows up to the top, then out of the grid onto the selected range.
        repeat(5) { press(Key.DirectionUp) }
        rule.onNodeWithText("101–150").assertIsFocused()
    }

    @Test
    fun `moving along the ranges switches the grid`() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        rule.onNodeWithText("151–200").assertIsFocused()
        rule.onNodeWithText("151").assertExists()
        rule.onNodeWithText("142").assertDoesNotExist()
    }
}
