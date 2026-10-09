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
import android.os.SystemClock
import org.junit.After
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
    /** Home redraws after loading and drops its focused element, without taking the focus back itself. */
    private var homeRedrawn by mutableStateOf(false)
    private var exits = 0

    /** The menu state is app-wide; a test must not inherit it from the one before. */
    @Before fun resetMenu() { DrawerBrowsing.active = false; DrawerBrowsing.lastMenuKeyAt = NEVER }
    @After fun leaveMenuClosed() = resetMenu()

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
                destination == Destination.Home && homeRedrawn -> Button(onClick = {}) { Text("Home redrawn") }
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

    /** Applies pending state changes first (they schedule the next frame), then lets [ms] pass. */
    private fun advance(ms: Long) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    private fun press(keys: List<Key>) = keys.forEach { key ->
        // Like MainActivity.dispatchKeyEvent: Left marks that the viewer asked for the menu.
        if (key == Key.DirectionLeft) DrawerBrowsing.lastMenuKeyAt = SystemClock.uptimeMillis()
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
        rule.runOnUiThread { profileLoaded = true }
        advance(500)
        rule.onNodeWithText("Sign out").assertIsFocused()
        rule.onNodeWithText(label(R.string.profile)).assertDoesNotExist()
    }

    @Test
    fun `focus that falls into the menu by itself does not open it and goes back to the screen`() {
        launch()
        rule.onNodeWithText("Home content").assertIsFocused()
        rule.runOnUiThread { homeRedrawn = true }
        advance(1_000)
        // The menu stayed collapsed (labels only show while it is open) and the screen has the focus.
        rule.onNodeWithText(label(R.string.home)).assertDoesNotExist()
        rule.onNodeWithText("Home redrawn").assertIsFocused()
    }

    @Test
    fun `Up past the top item stays in the menu even when the screen has something higher`() {
        launch()
        // On Search (its stand-in button sits at the very top, higher than the Home item, like the
        // real search field), go into the menu and run Up into the top, as a held Up does.
        press(listOf(Key.DirectionLeft, Key.DirectionDown))
        advance(1_000)
        press(listOf(Key.DirectionUp, Key.DirectionUp, Key.DirectionUp))
        rule.onNodeWithText(label(R.string.home)).assertIsFocused()
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
        rule.runOnUiThread { profileLoaded = true }
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

    private companion object {
        /** A Left press long ago: the menu was not asked for. */
        const val NEVER = -1_000_000L
    }
}
