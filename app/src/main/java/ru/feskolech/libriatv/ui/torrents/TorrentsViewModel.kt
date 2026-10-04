package ru.feskolech.libriatv.ui.torrents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.domain.Torrent

sealed interface TorrentsUiState {
    data object Loading : TorrentsUiState
    data class Content(val torrents: List<Torrent>) : TorrentsUiState
    data class Error(val message: String) : TorrentsUiState
}

@HiltViewModel
class TorrentsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ApiRepository,
) : ViewModel() {
    private val releaseId: Int = checkNotNull(savedState.get<String>("releaseId")).toInt()
    private val _state = MutableStateFlow<TorrentsUiState>(TorrentsUiState.Loading)
    val state: StateFlow<TorrentsUiState> = _state

    /** Magnet shown as a QR code when no app on the device can open it. */
    private val _qrMagnet = MutableStateFlow<String?>(null)
    val qrMagnet: StateFlow<String?> = _qrMagnet

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = TorrentsUiState.Loading
        _state.value = when (val result = repository.torrents(releaseId)) {
            is ApiResult.Failure -> TorrentsUiState.Error(result.message)
            // Best quality first, then most seeded.
            is ApiResult.Success -> TorrentsUiState.Content(result.value.sortedWith(
                compareByDescending<Torrent> { it.quality?.filter(Char::isDigit)?.toIntOrNull() ?: 0 }
                    .thenByDescending { it.seeders ?: 0 },
            ))
        }
    }

    fun showQr(magnet: String?) { _qrMagnet.value = magnet }
}
