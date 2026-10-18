package ru.feskolech.libriatv.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.domain.FilterOption
import ru.feskolech.libriatv.ui.auth.ProfileContent
import ru.feskolech.libriatv.ui.components.DrawerBrowsing

/** Dialogs inside screens, driven with the remote's keys. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ScreenDialogsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun menuClosed() { DrawerBrowsing.active = false }

    private fun text(id: Int) = rule.activity.getString(id)
    private fun inDialog(label: String) = rule.onNode(hasText(label) and hasAnyAncestor(isDialog()))
    private fun SemanticsNodeInteraction.press(key: Key) = performKeyInput { pressKey(key) }

    @Test
    fun `sign out asks first, Cancel is focused and keeps the account`() {
        var signedOut = 0
        rule.setContent { MaterialTheme { ProfileContent("Ada", null, onOpenMenu = {}, logout = { signedOut++ }) } }
        rule.onNode(hasText(text(R.string.auth_logout)) and !hasAnyAncestor(isDialog())).apply {
            assertIsFocused()
            press(Key.DirectionCenter)
        }
        inDialog(text(R.string.logout_question)).assertExists()
        inDialog(text(R.string.cancel)).apply { assertIsFocused(); press(Key.DirectionCenter) }
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, signedOut) }
    }

    @Test
    fun `confirming signs out`() {
        var signedOut = 0
        rule.setContent { MaterialTheme { ProfileContent("Ada", null, onOpenMenu = {}, logout = { signedOut++ }) } }
        rule.onNode(hasText(text(R.string.auth_logout)) and !hasAnyAncestor(isDialog())).press(Key.DirectionCenter)
        inDialog(text(R.string.cancel)).press(Key.DirectionLeft)
        inDialog(text(R.string.logout_confirm)).apply { assertIsFocused(); press(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(1, signedOut) }
    }
}
