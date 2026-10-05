package ru.feskolech.libriatv.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.data.repo.AuthRepository
import ru.feskolech.libriatv.data.repo.AuthState
import ru.feskolech.libriatv.data.repo.FavoritesRepository
import ru.feskolech.libriatv.domain.FilterOption
import ru.feskolech.libriatv.domain.Release

data class FavoritesUiState(
    val authorized: Boolean = false,
    val loading: Boolean = true,
    val releases: List<Release> = emptyList(),
    val ids: Set<Int> = emptySet(),
    val sorting: List<FilterOption> = emptyList(),
    val selectedSorting: String? = null,
    val page: Int = 0,
    val totalPages: Int = 1,
    val error: String? = null,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: FavoritesRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state
    private var loading = false

    init {
        viewModelScope.launch {
            auth.state.collect { state ->
                when (state) {
                    is AuthState.Authorized -> {
                        _state.value = _state.value.copy(authorized = true)
                        reload()
                    }
                    AuthState.Guest -> _state.value = FavoritesUiState(loading = false)
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            repository.ids.collect { ids -> _state.value = _state.value.copy(ids = ids) }
        }
    }

    fun reload() {
        if (loading || auth.state.value !is AuthState.Authorized) return
        viewModelScope.launch {
            if (_state.value.sorting.isEmpty()) {
                val options = repository.sorting()
                // Without an explicit choice show the API's default (its first option) instead of "Any".
                if (options is ApiResult.Success) _state.value = _state.value.copy(sorting = options.value,
                    selectedSorting = _state.value.selectedSorting ?: options.value.firstOrNull()?.id)
            }
            load(1)
        }
    }

    fun sort(id: String?) {
        _state.value = _state.value.copy(selectedSorting = id)
        reload()
    }

    fun loadMore() {
        val current = _state.value
        if (!loading && current.page < current.totalPages) viewModelScope.launch { load(current.page + 1) }
    }

    private suspend fun load(page: Int) {
        if (loading) return
        loading = true
        _state.value = _state.value.copy(loading = true, error = null)
        val result = repository.releases(page, _state.value.selectedSorting)
        when (result) {
            is ApiResult.Success -> {
                repository.refreshIds()
                _state.value = _state.value.copy(loading = false,
                    releases = if (page == 1) result.value.releases else (_state.value.releases + result.value.releases).distinctBy { it.id },
                    page = result.value.page, totalPages = result.value.totalPages)
            }
            is ApiResult.Failure -> _state.value = _state.value.copy(loading = false, error = result.message)
        }
        loading = false
    }
}
