package ru.feskolech.libriatv.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.feskolech.libriatv.ui.components.AccentButton as Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.WideButtonScale
import ru.feskolech.libriatv.ui.components.makeQr

@Composable
fun SettingsScreen(
    onContentFocus: () -> Unit,
    checkUpdates: () -> Unit,
    updateState: UpdateUiState,
    latestNotes: String,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var about by remember { mutableStateOf(false) }
    BackHandler(about) { about = false }
    if (about) {
        AboutScreen(latestNotes, checkUpdates, updateState, onBack = { about = false })
        return
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(state is SettingsUiState.Content) {
        if (state is SettingsUiState.Content) { withFrameNanos { }; runCatching { first.requestFocus() } }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .onFocusChanged { if (it.hasFocus) onContentFocus() }
        .padding(start = 48.dp, end = 48.dp, top = 27.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge, color = Color.White)
        val content = state as? SettingsUiState.Content ?: return@Column
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SettingTitle(R.string.settings_appearance) }
            item { SettingChoices(R.string.settings_theme,
                listOf(stringResource(R.string.settings_theme_dark), stringResource(R.string.settings_theme_oled)),
                if (content.appearance.oled) 1 else 0, viewModel::oled, listOf(false, true), Modifier.focusRequester(first)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_accent), color = Color.LightGray)
                    val names = listOf(R.string.accent_red, R.string.accent_orange, R.string.accent_yellow,
                        R.string.accent_green, R.string.accent_blue, R.string.accent_purple)
                    names.chunked(3).forEachIndexed { row, group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEachIndexed { column, name ->
                                val index = row * 3 + column
                                Button(onClick = { viewModel.accent(index) }) {
                                    Text(stringResource(name) + if (content.appearance.accent == index) " ✓" else "")
                                }
                            }
                        }
                    }
                }
            }
            item { SettingChoices(R.string.settings_ui_scale, listOf("90%", "100%", "115%", "130%"),
                listOf(90, 100, 115, 130).indexOf(content.appearance.scale), viewModel::scale,
                listOf(90, 100, 115, 130)) }
            item { SettingTitle(R.string.settings_playback) }
            item { SettingChoices(R.string.settings_quality, listOf("480p", "720p", "1080p"),
                listOf(480, 720, 1080).indexOf(content.quality), viewModel::quality,
                listOf(480, 720, 1080)) }
            item { SettingToggle(R.string.settings_auto_skip_opening, content.autoSkipOpening, viewModel::autoSkipOpening) }
            item { SettingToggle(R.string.settings_auto_skip_ending, content.autoSkipEnding, viewModel::autoSkipEnding) }
            item { SettingToggle(R.string.settings_auto_next, content.autoNext, viewModel::autoNext) }
            item { SettingToggle(R.string.settings_frame_rate, content.frameRateMatch, viewModel::frameRateMatch) }
            item { SettingToggle(R.string.night_mode, content.nightMode, viewModel::nightMode, R.string.night_mode_hint) }
            item { SettingToggle(R.string.settings_home_video_preview, content.homeVideoPreview, viewModel::homeVideoPreview) }
            item { SettingChoices(R.string.settings_speed, listOf("0.75×", "1×", "1.25×", "1.5×", "2×"),
                listOf(.75f, 1f, 1.25f, 1.5f, 2f).indexOf(content.speed), viewModel::speed,
                listOf(.75f, 1f, 1.25f, 1.5f, 2f)) }
            item { SettingTitle(R.string.settings_network) }
            item { SettingChoices(R.string.settings_mirror, listOf("anilibria.top", "aniliberty.top"),
                listOf("anilibria.top", "aniliberty.top").indexOf(content.mirror), viewModel::mirror,
                listOf("anilibria.top", "aniliberty.top")) }
            item { SettingToggle(R.string.settings_phone_remote, content.phoneRemoteEnabled, viewModel::phoneRemote) }
            if (content.phoneRemoteEnabled) {
                item {
                    val url = content.phoneRemoteUrl
                    if (url != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val qr = remember(url) { makeQr(url, 180) }
                            Image(qr.asImageBitmap(), contentDescription = stringResource(R.string.settings_phone_remote_qr),
                                modifier = Modifier.size(130.dp).background(Color.White).padding(6.dp))
                            Column {
                                Text(stringResource(R.string.settings_phone_remote_hint), color = Color.LightGray)
                                content.phoneRemotePin?.let { Text(stringResource(R.string.settings_phone_remote_pin, it), color = Color.White) }
                                Text(url.substringBefore("/?token=") + "/?pin=" + content.phoneRemotePin.orEmpty(), color = Color.White)
                            }
                        }
                    } else if (content.phoneRemoteError) {
                        Text(stringResource(R.string.settings_phone_remote_error), color = Color.LightGray)
                    }
                }
            }
            item { SettingTitle(R.string.settings_crash_reports) }
            item {
                if (content.crashReportsAvailable) {
                    SettingToggle(R.string.settings_crash_reports_automatic, content.automaticCrashReports,
                        viewModel::automaticCrashReports)
                } else {
                    Text(stringResource(R.string.settings_crash_reports_unavailable), color = Color.LightGray)
                }
            }
            item { SettingTitle(R.string.settings_about) }
            item { Button(onClick = { about = true }) { Text(stringResource(R.string.settings_about_open)) } }
        }
    }
}

@Composable
private fun SettingTitle(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleLarge,
        color = Color.White, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun SettingToggle(title: Int, enabled: Boolean, onClick: () -> Unit, hint: Int? = null) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), scale = WideButtonScale) {
        Column {
            Text(stringResource(title) + "  " + stringResource(if (enabled) R.string.settings_on else R.string.settings_off))
            if (hint != null) Text(stringResource(hint), style = androidx.tv.material3.MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun <T> SettingChoices(title: Int, labels: List<String>, selected: Int, onSelect: (T) -> Unit,
    values: List<T>, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), color = Color.LightGray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                Button(onClick = { onSelect(values[index]) }, modifier = if (index == 0) modifier else Modifier) {
                    Text(label + if (index == selected) " ✓" else "")
                }
            }
        }
    }
}
