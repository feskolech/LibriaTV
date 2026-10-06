package ru.feskolech.libriatv.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * True while the viewer moves through the side menu. Sections open as a preview as soon as their
 * menu item is focused, so a screen must not take the focus for its first control while this is set;
 * the focus stays in the menu until the viewer goes right or presses OK.
 */
object DrawerBrowsing {
    var active by mutableStateOf(false)

    /** Uptime of the last Left press: the key that legitimately takes the viewer into the menu. */
    var lastMenuKeyAt = 0L
}
