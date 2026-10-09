package ru.feskolech.libriatv.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.DrawerBrowsing

/**
 * The frame's focus rules between the side menu and the screens, driven with the remote's keys.
 * Screens are stand-ins that behave like the real ones: they take the focus when they appear (unless
 * the menu is open) and collapse the menu when the focus enters them.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AppShellTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** The profile stand-in shows "Loading" until the test says the request has finished. */
    private var profileLoaded by mutableStateOf(false)
    private var exits = 0

    /** The menu state is app-wide; a test must not inherit it from the one before. */
    @Before fun resetMenu() { DrawerBrowsing.active = false; DrawerBrowsing.lastMenuKeyAt = 0 }

    private fun launch() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                AppShell(rememberNavController(), onExit = { exits++ }) { shell ->
                    Destination.entries.forEach { destination ->
                        composable(destination.route) { StandIn(destination, shell) }
                    }
                }
            }
        }
        advance(500)
    }

    @Composable
    private fun StandIn(destination: Destination, shell: ShellScope) {
        Box(Modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) shell.closeMenu() }) {
            when {
                destination == Destination.Profile && !profileLoaded -> Text("Loading profile")
                destination == Destination.Profile -> FocusOnShow("Sign out")
                else -> FocusOnShow("${destination.name} content")
            }
        }
    }

    @Composable
    private fun FocusOnShow(label: String) {
        val focus = remember { FocusRequester() }
        // Like the real screens: an open menu keeps the focus, the screen does not grab it.
        LaunchedEffect(Unit) { withFrameNanos { }; if (!DrawerBrowsing.active) runCatching { focus.requestFocus() } }
        Button(onClick = {}, modifier = Modifier.focusRequester(focus)) { Text(label) }
    }

    private fun advance(ms: Long) {
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    private fun press(keys: List<Key>) = keys.forEach { key ->
        rule.onNode(isFocused()).performKeyInput { pressKey(key) }
        advance(16)
    }

    private fun label(id: Int) = rule.activity.getString(id)

    private fun downToProfileAndOk() {
        press(listOf(Key.DirectionLeft))
        // Quickly down the menu, as a viewer does on a cold start.
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionDown, Key.DirectionDown, Key.DirectionDown))
        rule.onNodeWithText(label(R.string.profile)).assertIsFocused()
        press(listOf(Key.DirectionCenter))
    }

    @Test
    fun `OK on a section that is still loading enters it as soon as it has loaded`() {
        launch()
        rule.onNodeWithText("Home content").assertIsFocused()
        downToProfileAndOk()

        // A slow link: the profile takes several seconds. The menu waits with it…
        advance(3_000)
        rule.onNodeWithText("Loading profile").assertExists()
        rule.onNodeWithText(label(R.string.profile)).assertIsFocused()

        // …and hands the focus over the moment there is something to focus; the menu collapses
        // (its labels are only shown while it is open).
        profileLoaded = true
        advance(500)
        rule.onNodeWithText("Sign out").assertIsFocused()
        rule.onNodeWithText(label(R.string.profile)).assertDoesNotExist()
    }

    @Test
    fun `OK on a loaded section goes straight into it`() {
        launch()
        press(listOf(Key.DirectionLeft, Key.DirectionDown, Key.DirectionDown))
        press(listOf(Key.DirectionCenter))
        advance(1_000)
        rule.onNodeWithText("Catalog content").assertIsFocused()
        rule.onNodeWithText(label(R.string.catalog)).assertDoesNotExist()
    }

    @Test
    fun `moving on in the menu cancels the pending OK`() {
        launch()
        downToProfileAndOk()
        advance(1_000)
        // The viewer goes on to Settings before the profile has loaded: the focus stays in the menu.
        press(listOf(Key.DirectionDown))
        profileLoaded = true
        advance(2_000)
        rule.onNodeWithText(label(R.string.settings)).assertIsFocused()
    }

    @Test
    fun `Back on Home asks before leaving the app, Exit is focused`() {
        launch()
        // The dialog is a window of its own; its frames run on the real clock, not the paused test one.
        rule.mainClock.autoAdvance = true
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        val exit = rule.onNodeWithText(label(R.string.exit))
        exit.assertIsFocused()
        exit.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(1, exits) }
    }

}
