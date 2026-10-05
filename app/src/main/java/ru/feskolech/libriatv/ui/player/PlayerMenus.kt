package ru.feskolech.libriatv.ui.player

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** Icon-only playback button; the label goes to accessibility services. */
@Composable
internal fun ControlButton(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = stringResource(label), modifier = Modifier.size(28.dp))
    }
}

private enum class SettingsPage { Root, Quality, Speed }

/**
 * Side sheet with every player setting (quality, speed, opening skip, night mode).
 * Quality and speed open a sub-list; Back inside a sub-list returns to the root page.
 */
@Composable
internal fun PlayerSettingsMenu(
    content: PlayerContent,
    firstFocus: FocusRequester,
    onQuality: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onAutoSkipOpening: () -> Unit,
    onAutoSkipEnding: () -> Unit,
    onNightMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(SettingsPage.Root) }
    val qualities = listOf(1080 to content.episode.hls1080, 720 to content.episode.hls720, 480 to content.episode.hls480)
        .filter { !it.second.isNullOrBlank() }.map { it.first }

    LaunchedEffect(page) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier.fillMaxHeight().width(380.dp).background(Color(0xF0141416))
            .padding(horizontal = 24.dp, vertical = 27.dp)
            // Back on a sub-page goes up one level instead of closing the menu.
            .onPreviewKeyEvent { event ->
                if (page != SettingsPage.Root && event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    page = SettingsPage.Root
                    true
                } else event.key == Key.Back && page != SettingsPage.Root
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val title = when (page) {
            SettingsPage.Root -> R.string.player_settings
            SettingsPage.Quality -> R.string.player_quality
            SettingsPage.Speed -> R.string.player_speed
        }
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp))
        when (page) {
            SettingsPage.Root -> {
                MenuRow(stringResource(R.string.player_quality), "${content.quality}p", Modifier.focusRequester(firstFocus), arrow = true) {
                    page = SettingsPage.Quality
                }
                MenuRow(stringResource(R.string.player_speed), formatSpeedLabel(content.speed), arrow = true) { page = SettingsPage.Speed }
                MenuRow(stringResource(R.string.auto_skip_opening), onOff(content.autoSkipOpening), onClick = onAutoSkipOpening)
                MenuRow(stringResource(R.string.auto_skip_ending), onOff(content.autoSkipEnding), onClick = onAutoSkipEnding)
                MenuRow(stringResource(R.string.night_mode), onOff(content.nightMode), onClick = onNightMode)
            }
            SettingsPage.Quality -> qualities.forEachIndexed { index, quality ->
                MenuRow("${quality}p", if (quality == content.quality) "✓" else "",
                    if (quality == content.quality || (index == 0 && content.quality !in qualities)) Modifier.focusRequester(firstFocus) else Modifier) {
                    onQuality(quality)
                    page = SettingsPage.Root
                }
            }
            SettingsPage.Speed -> SPEEDS.forEach { speed ->
                MenuRow(formatSpeedLabel(speed), if (speed == content.speed) "✓" else "",
                    if (speed == content.speed) Modifier.focusRequester(firstFocus) else Modifier) {
                    onSpeed(speed)
                    page = SettingsPage.Root
                }
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, value: String, modifier: Modifier = Modifier, arrow: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title)
            Spacer(Modifier.weight(1f))
            Text(value)
            if (arrow) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun onOff(value: Boolean) = stringResource(if (value) R.string.setting_on else R.string.setting_off)

private fun formatSpeedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "${"%.2f".format(java.util.Locale.US, speed).trimEnd('0')}×"
