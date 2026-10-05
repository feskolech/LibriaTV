package ru.feskolech.libriatv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.UpdateCheckResult
import ru.feskolech.libriatv.data.repo.UpdateChecker
import ru.feskolech.libriatv.data.repo.UpdateRelease

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: UpdateRelease) : UpdateUiState
    data class Downloading(val release: UpdateRelease, val percent: Int) : UpdateUiState
    data class Ready(val file: File) : UpdateUiState
    data object Current : UpdateUiState
    data object NoRelease : UpdateUiState
    data object Unavailable : UpdateUiState
    data object Failed : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(private val checker: UpdateChecker) : ViewModel() {
    val latestNotes = checker.latestNotes
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state
    private var busy = false

    fun check(force: Boolean = false) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            if (force) _state.value = UpdateUiState.Checking
            try {
                when (val result = checker.check(force)) {
                    is UpdateCheckResult.Available -> _state.value = UpdateUiState.Available(result.release)
                    UpdateCheckResult.Current -> if (force) _state.value = UpdateUiState.Current
                    UpdateCheckResult.NoRelease -> if (force) _state.value = UpdateUiState.NoRelease
                    UpdateCheckResult.Unavailable -> if (force) _state.value = UpdateUiState.Unavailable
                    UpdateCheckResult.Skipped -> Unit
                }
            } finally { busy = false }
        }
    }

    fun download(release: UpdateRelease) {
        if (busy) return
        busy = true
        _state.value = UpdateUiState.Downloading(release, 0)
        viewModelScope.launch {
            try {
                val file = checker.download(release) { percent ->
                    _state.value = UpdateUiState.Downloading(release, percent)
                }
                _state.value = UpdateUiState.Ready(file)
            } catch (_: Exception) {
                _state.value = UpdateUiState.Failed
            } finally { busy = false }
        }
    }

    fun dismiss() { if (!busy) _state.value = UpdateUiState.Idle }
}
