package ru.feskolech.libriatv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.PlaybackStore
import ru.feskolech.libriatv.data.repo.SettingsStore
import ru.feskolech.libriatv.crash.CrashReportManager
import ru.feskolech.libriatv.remote.PhoneRemote

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Content(
        val quality: Int,
        val autoSkip: Boolean,
        val autoNext: Boolean,
        val frameRateMatch: Boolean,
        val nightMode: Boolean,
        val speed: Float,
        val mirror: String,
        val crashReportsAvailable: Boolean,
        val automaticCrashReports: Boolean,
        val phoneRemoteEnabled: Boolean,
        val phoneRemoteUrl: String?,
        val phoneRemoteError: Boolean,
    ) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playback: PlaybackStore,
    private val settings: SettingsStore,
    private val crashReports: CrashReportManager,
    private val phoneRemote: PhoneRemote,
) : ViewModel() {
    private val _state = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            _state.value = SettingsUiState.Content(playback.quality(), playback.autoSkip(),
                playback.autoNext(), playback.frameRateMatch(), playback.nightMode(), playback.speed(), settings.mirror(),
                crashReports.available, crashReports.automatic(), false, null, false)
            phoneRemote.state.collect { remote ->
                val current = _state.value as? SettingsUiState.Content ?: return@collect
                _state.value = current.copy(phoneRemoteEnabled = remote.enabled, phoneRemoteUrl = remote.url,
                    phoneRemoteError = remote.error)
            }
        }
    }

    private fun change(save: suspend (SettingsUiState.Content) -> Unit, update: (SettingsUiState.Content) -> SettingsUiState.Content) {
        val next = (_state.value as? SettingsUiState.Content)?.let(update) ?: return
        _state.value = next
        viewModelScope.launch { save(next) }
    }

    fun quality(value: Int) = change({ playback.setQuality(it.quality) }, { it.copy(quality = value) })
    fun autoSkip() = change({ playback.setAutoSkip(it.autoSkip) }, { it.copy(autoSkip = !it.autoSkip) })
    fun autoNext() = change({ playback.setAutoNext(it.autoNext) }, { it.copy(autoNext = !it.autoNext) })
    fun frameRateMatch() = change({ playback.setFrameRateMatch(it.frameRateMatch) }, { it.copy(frameRateMatch = !it.frameRateMatch) })
    fun nightMode() = change({ playback.setNightMode(it.nightMode) }, { it.copy(nightMode = !it.nightMode) })
    fun speed(value: Float) = change({ playback.setSpeed(it.speed) }, { it.copy(speed = value) })
    fun mirror(value: String) = change({ settings.setMirror(it.mirror) }, { it.copy(mirror = value) })
    fun automaticCrashReports() = change(
        { crashReports.setAutomatic(it.automaticCrashReports) },
        { it.copy(automaticCrashReports = !it.automaticCrashReports) },
    )
    fun phoneRemote() = viewModelScope.launch {
        val current = _state.value as? SettingsUiState.Content ?: return@launch
        phoneRemote.setEnabled(!current.phoneRemoteEnabled)
    }
}
