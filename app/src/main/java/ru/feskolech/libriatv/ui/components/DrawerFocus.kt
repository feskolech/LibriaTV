package ru.feskolech.libriatv.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/**
 * The side menu's current item. Plain geometric focus search sends Left from a control to whatever
 * focusable lies left of it (e.g. the seasons row under the release poster); screens point their
 * leftmost controls here so Left always opens the menu.
 */
val LocalDrawerFocus = staticCompositionLocalOf<FocusRequester?> { null }
