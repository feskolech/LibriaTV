package ru.feskolech.libriatv.ui.components

import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale

/**
 * Full-width rows (settings, episodes, torrents, menus) must not grow on focus: the default 1.1x
 * scale pushes them past the screen/drawer edges. Focus stays visible through the white container.
 */
val WideButtonScale: ButtonScale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 0.98f)
