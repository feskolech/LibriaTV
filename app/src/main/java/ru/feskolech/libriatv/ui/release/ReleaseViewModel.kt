package ru.feskolech.libriatv.ui.release

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
import ru.feskolech.libriatv.data.repo.PlaybackProgress
import ru.feskolech.libriatv.data.repo.PlaybackStore
import ru.feskolech.libriatv.domain.Release

sealed interface ReleaseUiState {
    data object Loading : ReleaseUiState
    data class Content(val release: Release, val progress: Map<String, PlaybackProgress>) : ReleaseUiState
    data class Error(val message: String) : ReleaseUiState
}

@HiltViewModel
class ReleaseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ApiRepository,
    private val playbackStore: PlaybackStore,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["id"])
    private val _state = MutableStateFlow<ReleaseUiState>(ReleaseUiState.Loading)
    val state: StateFlow<ReleaseUiState> = _state
    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = ReleaseUiState.Loading
        when (val result = repository.release(id)) {
            is ApiResult.Failure -> _state.value = ReleaseUiState.Error(result.message)
            is ApiResult.Success -> _state.value = ReleaseUiState.Content(
                result.value,
                result.value.episodes.mapNotNull { episode ->
                    playbackStore.progress(episode.id)?.let { episode.id to it }
                }.toMap(),
            )
        }
    }
}
