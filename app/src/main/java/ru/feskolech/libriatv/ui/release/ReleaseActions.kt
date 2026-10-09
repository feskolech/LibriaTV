package ru.feskolech.libriatv.ui.release

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import ru.feskolech.libriatv.ui.components.AccentButton
import ru.feskolech.libriatv.ui.components.LocalDrawerFocus

/** One secondary action of the release page: an icon, and the label it shows while focused. */
internal class ReleaseAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * The release page's actions: the main one (watch / continue) as a labelled button, the rest as round icon
 * buttons, with the focused one's name on a line below. Replaces the 3×2 grid of equal buttons, where
 * nothing said which action mattered. The buttons keep their size when focused, so moving between them
 * never shifts the row; they wrap to a second line if a long main label leaves no room.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReleaseActions(mainLabel: String, mainEnabled: Boolean, onMain: () -> Unit, actions: List<ReleaseAction>,
    mainModifier: Modifier = Modifier, modifier: Modifier = Modifier) {
    // From the main button (leftmost), Left goes straight to the side menu.
    val drawer = LocalDrawerFocus.current
    var focusedLabel by remember { mutableStateOf<String?>(null) }
    Column(modifier.onFocusChanged { if (!it.hasFocus) focusedLabel = null }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically) {
        AccentButton(onClick = onMain, enabled = mainEnabled,
            modifier = mainModifier.then(if (drawer != null) Modifier.focusProperties { left = drawer } else Modifier)
                .onFocusChanged { if (it.isFocused) focusedLabel = null },
            contentPadding = PaddingValues(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(mainLabel, Modifier.padding(start = 6.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions.forEach { action -> IconAction(action, onFocused = { focusedLabel = action.label }) }
    }
    // Always one line high, so the page below does not move as the focus passes the buttons.
    Text(focusedLabel.orEmpty(), fontSize = 18.sp, color = Color(0xFFB4B4BC), maxLines = 1)
    }
}

@Composable
private fun IconAction(action: ReleaseAction, onFocused: () -> Unit) {
    AccentButton(onClick = action.onClick,
        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() }.semantics { contentDescription = action.label },
        contentPadding = PaddingValues(10.dp)) {
        Icon(action.icon, contentDescription = null, modifier = Modifier.size(26.dp))
    }
}

/** Icons of the page's actions; the list one shows a check once the title is in one of the lists. */
internal object ReleaseIcons {
    fun favorite(inFavorites: Boolean) = if (inFavorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
    fun list(inList: Boolean) = if (inList) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd
    fun rating(rated: Boolean) = if (rated) Icons.Filled.Star else Icons.Filled.StarBorder
    val torrents = Icons.Filled.Download
}
