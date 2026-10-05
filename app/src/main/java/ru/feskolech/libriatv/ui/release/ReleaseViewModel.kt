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
import ru.feskolech.libriatv.data.repo.LibraryRepository
import ru.feskolech.libriatv.domain.UserList
import kotlinx.coroutines.async

sealed interface ReleaseUiState {
    data object Loading : ReleaseUiState
    data class Content(val release: Release, val progress: Map<String, PlaybackProgress>,
        val favorite: Boolean, val authorized: Boolean, val favoriteError: String? = null,
        val similar: List<Release> = emptyList(),
        val seasons: List<Release> = emptyList(),
        val list: UserList? = null, val rating: Int? = null) : ReleaseUiState
    data class Error(val message: String) : ReleaseUiState
}

@HiltViewModel
class ReleaseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ApiRepository,
    private val progressRepository: ProgressRepository,
    private val favorites: FavoritesRepository,
    private val auth: AuthRepository,
    private val library: LibraryRepository,
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
                val release = result.value
                val seasons = async { library.seasons(release) }
                val list = async { if (authorized) library.listOf(release.id) else null }
                val rating = async { if (authorized) library.ownRating(release.id) else null }
                val similar = (repository.recommended(releaseId = release.id) as? ApiResult.Success)?.value.orEmpty()
                    .filter { it.id != release.id }
                val current = _state.value as? ReleaseUiState.Content
                if (current?.release?.id == release.id) _state.value = current.copy(similar = similar,
                    seasons = seasons.await(), list = list.await(), rating = rating.await())
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

    fun setList(list: UserList?, onLogin: () -> Unit) {
        val content = _state.value as? ReleaseUiState.Content ?: return
        if (!content.authorized) { onLogin(); return }
        val previous = content.list
        _state.value = content.copy(list = list)
        viewModelScope.launch {
            if (!library.setList(content.release.id, list)) {
                (_state.value as? ReleaseUiState.Content)?.let { _state.value = it.copy(list = previous) }
            }
        }
    }

    fun rate(score: Int?, onLogin: () -> Unit) {
        val content = _state.value as? ReleaseUiState.Content ?: return
        if (!content.authorized) { onLogin(); return }
        val previous = content.rating
        _state.value = content.copy(rating = score)
        viewModelScope.launch {
            if (!library.rate(content.release.id, score)) {
                (_state.value as? ReleaseUiState.Content)?.let { _state.value = it.copy(rating = previous) }
            }
        }
    }
}
