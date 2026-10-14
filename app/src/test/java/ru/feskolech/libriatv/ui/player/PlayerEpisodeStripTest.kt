package ru.feskolech.libriatv.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
// A TV-sized screen, like the player.
@Config(qualifiers = "w960dp-h540dp-xhdpi")
class PlayerEpisodeStripTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    // No frames: each card shows its number, which the tests look for.
    private fun release(count: Int) = Release(1, "t", null, null, null, null, emptyList(),
        (1..count).map { Episode("e$it", "", it.toDouble(), null, null, null, "sd", null, null) }, null)

    private var ups = 0

    /** The player's strip on episode [playing], with the focus where the player puts it when the strip opens. */
    private fun show(count: Int, playing: Int) = rule.setContent {
        MaterialTheme {
            val focus = remember { FocusRequester() }
            val r = remember { release(count) }
            PlayerEpisodeStrip(PlayerContent(r, r.episodes[playing - 1], 1080), focus, onPlay = {}, onUp = { ups++ })
            // As the player does: the lazy strip composes its cards a frame later.
            LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; focus.requestFocus() }
        }
    }

    private fun press(key: Key) = rule.onNode(isFocused()).performKeyInput { pressKey(key) }

    @Test
    fun `opens on the playing episode and Up leaves a short strip`() {
        show(13, playing = 11)
        rule.onNodeWithText("11").assertExists()
        press(Key.DirectionUp)
        assertEquals(1, ups)
    }

    @Test
    fun `a long series goes through its ranges before leaving`() {
        show(355, playing = 142)
        // From the playing episode Up lands on its range, not out of the strip.
        press(Key.DirectionUp)
        rule.onNodeWithText("101–150").assertIsFocused()
        assertEquals(0, ups)
        // Moving along the ranges switches the strip.
        press(Key.DirectionRight)
        rule.onNodeWithText("151–200").assertIsFocused()
        rule.onNodeWithText("151").assertExists()
        // Up from the ranges leaves the strip.
        press(Key.DirectionUp)
        assertEquals(1, ups)
    }
}
