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
import ru.feskolech.libriatv.data.repo.ProgressRepository
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.data.repo.FavoritesRepository
import ru.feskolech.libriatv.data.repo.AuthRepository
import ru.feskolech.libriatv.data.repo.AuthState

sealed interface ReleaseUiState {
    data object Loading : ReleaseUiState
    data class Content(val release: Release, val progress: Map<String, PlaybackProgress>,
        val favorite: Boolean, val authorized: Boolean, val favoriteError: String? = null,
        val similar: List<Release> = emptyList()) : ReleaseUiState
    data class Error(val message: String) : ReleaseUiState
}

@HiltViewModel
class ReleaseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ApiRepository,
    private val progressRepository: ProgressRepository,
    private val favorites: FavoritesRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["id"])
    private val _state = MutableStateFlow<ReleaseUiState>(ReleaseUiState.Loading)
    val state: StateFlow<ReleaseUiState> = _state
    init {
        refresh()
        viewModelScope.launch {
            favorites.ids.collect { ids ->
                val content = _state.value as? ReleaseUiState.Content ?: return@collect
                _state.value = content.copy(favorite = content.release.id in ids)
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.value = ReleaseUiState.Loading
        when (val result = repository.release(id)) {
            is ApiResult.Failure -> _state.value = ReleaseUiState.Error(result.message)
            is ApiResult.Success -> {
                val authorized = auth.state.value is AuthState.Authorized
                if (authorized) favorites.refreshIds()
                _state.value = ReleaseUiState.Content(result.value,
                    progressRepository.releaseProgress(result.value),
                    result.value.id in favorites.ids.value, authorized)
                val similar = (repository.recommended(releaseId = result.value.id) as? ApiResult.Success)?.value.orEmpty()
                    .filter { it.id != result.value.id }
                val current = _state.value as? ReleaseUiState.Content
                if (current?.release?.id == result.value.id) _state.value = current.copy(similar = similar)
            }
        }
    }

    fun toggleFavorite(onLogin: () -> Unit) {
        val content = _state.value as? ReleaseUiState.Content ?: return
        if (!content.authorized) { onLogin(); return }
        viewModelScope.launch {
            val result = favorites.toggle(content.release.id)
            if (result is ApiResult.Failure) {
                val latest = _state.value as? ReleaseUiState.Content ?: return@launch
                _state.value = latest.copy(favoriteError = result.message)
            }
        }
    }
}
