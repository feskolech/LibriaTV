package ru.feskolech.libriatv.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The shared dialog: actions in one row, equal width, the requested one focused, D-pad moves between them. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class AppDialogTest {
    @get:Rule val rule = createComposeRule()

    private var confirmed = 0
    private var cancelled = 0

    private fun showSignOutLikeDialog() = rule.setContent {
        MaterialTheme {
            AppDialog(onDismiss = { cancelled++ }, title = "Sign out?", width = 480.dp, actions = {
                DialogButton("Sign out", { confirmed++ })
                DialogButton("Cancel", { cancelled++ }, initialFocus = true)
            })
        }
    }

    @Test
    fun `the safe action is focused and the buttons are equal`() {
        showSignOutLikeDialog()
        rule.onNodeWithText("Cancel").assertIsFocused()
        val confirm = rule.onNodeWithText("Sign out").fetchSemanticsNode().boundsInRoot
        val cancel = rule.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot
        assertEquals(confirm.top, cancel.top, 0.5f)
        assertEquals(confirm.width, cancel.width, 0.5f)
    }

    @Test
    fun `left moves to the other action and OK triggers it`() {
        showSignOutLikeDialog()
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }
        rule.onNodeWithText("Sign out").assertIsFocused()
        rule.onNodeWithText("Cancel").assertIsNotFocused()
        rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        rule.runOnIdle { assertEquals(1, confirmed); assertEquals(0, cancelled) }
    }
}
