package ru.feskolech.libriatv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpRect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItemColors
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.TabColors
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRowDefaults

/**
 * One focus language for the whole app: what has the focus is filled with the accent chosen in Settings
 * (text in its contrast colour); what is chosen but not focused (the current section, tab or day) gets
 * only this light tint or a thin light outline. Several components used to default to a white focus,
 * so changing the accent changed only half the screens.
 */
object FocusStyle {
    val SelectedTint = Color(0x29FFFFFF)
    val SelectedOutline = Color(0xFFB4B4BC)
}

/** Side menu items: accent when focused, a light tint for the current section. */
@Composable
fun accentDrawerItemColors(): NavigationDrawerItemColors {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    return NavigationDrawerItemDefaults.colors(
        contentColor = Color.White,
        focusedContainerColor = accent, focusedContentColor = onAccent,
        pressedContainerColor = accent, pressedContentColor = onAccent,
        selectedContainerColor = FocusStyle.SelectedTint, selectedContentColor = Color.White,
        focusedSelectedContainerColor = accent, focusedSelectedContentColor = onAccent,
        pressedSelectedContainerColor = accent, pressedSelectedContentColor = onAccent,
    )
}

/** Tab text: white, and the accent's contrast colour on the focused (accent) pill. */
@Composable
fun accentTabColors(): TabColors = TabDefaults.pillIndicatorTabColors(
    contentColor = Color.White,
    selectedContentColor = Color.White,
    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
)

/** The tab row's pill: accent while the row has the focus, a light tint on the chosen tab otherwise. */
@Composable
fun AccentTabIndicator(positions: List<DpRect>, selected: Int, rowHasFocus: Boolean) {
    positions.getOrNull(selected)?.let { position ->
        TabRowDefaults.PillIndicator(currentTabPosition = position, doesTabRowHaveFocus = rowHasFocus,
            activeColor = MaterialTheme.colorScheme.primary, inactiveColor = FocusStyle.SelectedTint)
    }
}
