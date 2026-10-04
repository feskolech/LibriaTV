package ru.feskolech.libriatv.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.domain.CatalogFilter
import ru.feskolech.libriatv.domain.CatalogReferences
import ru.feskolech.libriatv.domain.Release

data class CatalogUiState(
    val references: CatalogReferences? = null,
    val filter: CatalogFilter = CatalogFilter(),
    val releases: List<Release> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canLoadMore: Boolean get() = !loading && page < totalPages
}

@HiltViewModel
class CatalogViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state
    private var pageJob: Job? = null

    init {
        loadReferences()
        reload()
    }

    fun loadReferences() = viewModelScope.launch {
        when (val result = repository.catalogReferences()) {
            is ApiResult.Success -> _state.value = _state.value.copy(
                references = result.value,
                filter = _state.value.filter.let { it.copy(sorting = it.sorting ?: result.value.sorting.firstOrNull()?.id) },
            )
            is ApiResult.Failure -> Unit // filters stay hidden; the grid still works with defaults
        }
    }

    fun applyFilter(filter: CatalogFilter) {
        if (filter == _state.value.filter) return
        _state.value = _state.value.copy(filter = filter)
        reload()
    }

    fun resetFilter() = applyFilter(CatalogFilter(sorting = _state.value.filter.sorting))

    fun reload() {
        pageJob?.cancel()
        _state.value = _state.value.copy(releases = emptyList(), page = 0, totalPages = 1, loading = false, error = null)
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        _state.value = current.copy(loading = true, error = null)
        pageJob = viewModelScope.launch {
            val next = current.page + 1
            when (val result = repository.catalog(current.filter, next)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    // The API can repeat a release across pages when data changes between requests.
                    releases = (_state.value.releases + result.value.releases).distinctBy { it.id },
                    page = result.value.page, totalPages = result.value.totalPages, loading = false,
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(loading = false, error = result.message)
            }
        }
    }
}
