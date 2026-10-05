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
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ru.feskolech.libriatv.BuildConfig
import ru.feskolech.libriatv.R
import ru.feskolech.libriatv.ui.components.WideButtonScale
import ru.feskolech.libriatv.ui.components.makeQr

private val githubUrl = "https://github.com/${BuildConfig.UPDATE_REPO}"

@Composable
fun SettingsScreen(
    onContentFocus: () -> Unit,
    checkUpdates: () -> Unit,
    updateState: UpdateUiState,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(state is SettingsUiState.Content) {
        if (state is SettingsUiState.Content) { withFrameNanos { }; runCatching { first.requestFocus() } }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF101010))
        .onFocusChanged { if (it.hasFocus) onContentFocus() }
        .padding(start = 48.dp, end = 48.dp, top = 27.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge, color = Color.White)
        val content = state as? SettingsUiState.Content ?: return@Column
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SettingTitle(R.string.settings_playback) }
            item { SettingChoices(R.string.settings_quality, listOf("480p", "720p", "1080p"),
                listOf(480, 720, 1080).indexOf(content.quality), viewModel::quality,
                listOf(480, 720, 1080), Modifier.focusRequester(first)) }
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
                                Text(stringResource(R.string.settings_phone_remote_pin, url.substringAfter("pin=")), color = Color.White)
                                Text(url, color = Color.White)
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
            item {
                Button(onClick = checkUpdates) { Text(stringResource(R.string.settings_check_updates)) }
            }
            item {
                val result = when (updateState) {
                    UpdateUiState.Checking -> R.string.settings_checking
                    UpdateUiState.Current -> R.string.settings_current
                    UpdateUiState.NoRelease -> R.string.settings_no_release
                    UpdateUiState.Unavailable -> R.string.settings_unavailable
                    UpdateUiState.Failed -> R.string.settings_download_failed
                    else -> null
                }
                if (result != null) Text(stringResource(result), color = Color.LightGray)
            }
            item { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = Color.LightGray) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val qr = remember { makeQr(githubUrl, 160) }
                    Image(qr.asImageBitmap(), contentDescription = stringResource(R.string.settings_github),
                        modifier = Modifier.size(110.dp).background(Color.White).padding(6.dp))
                    Text(githubUrl, color = Color.LightGray)
                }
            }
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
