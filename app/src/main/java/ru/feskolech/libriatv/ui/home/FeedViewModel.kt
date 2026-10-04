package ru.feskolech.libriatv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.domain.Release

data class FeedUiState(val releases: List<Release> = emptyList(), val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class FeedViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state
    private var limit = 0
    private var exhausted = false
    init { loadMore() }

    fun loadMore() {
        if (_state.value.loading || exhausted) return
        val nextLimit = limit + 30
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = repository.latest(nextLimit)) {
                is ApiResult.Success -> {
                    exhausted = result.value.size < nextLimit || result.value.size <= _state.value.releases.size
                    limit = nextLimit
                    _state.value = FeedUiState(result.value.sortedByDescending { it.freshAt })
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(loading = false, error = result.message)
            }
        }
    }
}
