package ru.feskolech.libriatv.ui.navigation

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import ru.feskolech.libriatv.ui.components.DrawerBrowsing

/** How long after Left a focus arriving in the menu still counts as "the user asked for the menu". */
private const val MENU_KEY_WINDOW_MS = 700L

internal fun menuRecentlyAsked() = SystemClock.uptimeMillis() - DrawerBrowsing.lastMenuKeyAt < MENU_KEY_WINDOW_MS

/**
 * The side menu's content. It only reports what happens (focus inside, an item focused or clicked);
 * the navigation decisions stay with [AppNavigation].
 *
 * @param pageOpen a page opened from a section (release, player…) is on screen: focus that merely falls
 *   into the menu while it replaces the previous screen is refused, or the drawer would slide open for a moment.
 */
@Composable
internal fun NavigationDrawerScope.SideMenu(
    drawerValue: DrawerValue,
    selected: Destination,
    hidden: Boolean,
    pageOpen: Boolean,
    itemFocus: Map<Destination, FocusRequester>,
    onFocusInside: (Boolean) -> Unit,
    onItemFocused: (Destination) -> Unit,
    onItemClick: (Destination) -> Unit,
) {
    val open = drawerValue == DrawerValue.Open
    val drawerColor by animateColorAsState(
        if (open) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background, label = "drawer")
    Column(
        modifier = Modifier
            .then(
                when {
                    hidden -> Modifier.width(0.dp)
                    open -> Modifier.width(280.dp)
                    // Fixed collapsed width: measuring intrinsics on every animation frame stuttered on TV boxes.
                    else -> Modifier.width(88.dp)
                }
            )
            .fillMaxHeight()
            // Same colour as the screens when collapsed, so no lighter strip shows while it animates;
            // only the open drawer stands out a little.
            .background(drawerColor)
            .padding(horizontal = 8.dp, vertical = 27.dp)
            .onFocusChanged { onFocusInside(it.hasFocus) }
            .selectableGroup()
            // Entering the drawer from content lands on the current section, not on the nearest row.
            .focusProperties {
                onEnter = {
                    val byArrow = requestedFocusDirection == FocusDirection.Left ||
                        requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down
                    if (!byArrow && !menuRecentlyAsked() && pageOpen) cancelFocusChange()
                    else itemFocus.getValue(selected).requestFocus()
                }
                // Only Right leaves the menu. Up from the top item (a held Up runs into it) or Down from
                // the bottom one would otherwise jump to whatever the screen has above or below (the
                // search field sits higher than Home) and close the menu.
                onExit = {
                    if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down)
                        cancelFocusChange()
                }
            }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Destination.entries.forEach { destination ->
            NavigationDrawerItem(
                selected = destination == selected,
                onClick = { onItemClick(destination) },
                leadingContent = { Icon(destination.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                modifier = Modifier
                    .focusRequester(itemFocus.getValue(destination))
                    // Open: items fill the drawer exactly, otherwise their default width overflows and the focus pill is clipped.
                    .then(if (open) Modifier.fillMaxWidth() else Modifier.width(72.dp))
                    .onFocusChanged { if (it.isFocused) onItemFocused(destination) },
            ) {
                if (open) Text(stringResource(destination.title), fontSize = 20.sp)
            }
        }
    }
}
