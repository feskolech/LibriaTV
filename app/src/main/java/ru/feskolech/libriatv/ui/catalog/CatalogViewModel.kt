package ru.feskolech.libriatv.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.domain.CatalogFilter
import ru.feskolech.libriatv.domain.CatalogReferences
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.sortedByTitle

data class CatalogUiState(
    val references: CatalogReferences? = null,
    val filter: CatalogFilter = CatalogFilter(),
    val releases: List<Release> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val error: String? = null,
    val titleSorted: Boolean = false,
    /** Pages fetched / total while building the title-sorted catalog; null when not doing that. */
    val titleProgress: Pair<Int, Int>? = null,
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

    fun sortByTitle() {
        _state.value = _state.value.copy(titleSorted = true, filter = _state.value.filter.copy(sorting = null))
        reload()
    }
    fun sortByApi(id: String) {
        _state.value = _state.value.copy(titleSorted = false, filter = _state.value.filter.copy(sorting = id))
        reload()
    }

    fun resetFilter() = applyFilter(CatalogFilter(sorting = _state.value.filter.sorting))

    fun reload() {
        pageJob?.cancel()
        _state.value = _state.value.copy(releases = emptyList(), page = 0, totalPages = 1, loading = false, error = null)
        if (_state.value.titleSorted) loadAllByTitle() else loadMore()
    }

    /** Title-sorted result per filter for this session: the API cannot sort by title itself. */
    private val titleCache = mutableMapOf<CatalogFilter, List<Release>>()

    /**
     * The API has no title sorting, so the whole (filtered) catalog is fetched as compact pages of 50,
     * four at a time, and shown once complete — a grid that re-sorts while loading jumps under focus.
     */
    private fun loadAllByTitle() {
        val filter = _state.value.filter
        titleCache[filter]?.let { cached ->
            _state.value = _state.value.copy(releases = cached, page = 1, totalPages = 1, loading = false)
            return
        }
        _state.value = _state.value.copy(loading = true)
        pageJob = viewModelScope.launch {
            val first = repository.catalog(filter, 1, TITLE_PAGE, compact = true)
            if (first is ApiResult.Failure) {
                _state.value = _state.value.copy(loading = false, titleProgress = null, error = first.message); return@launch
            }
            first as ApiResult.Success
            val total = first.value.totalPages
            var done = 1
            _state.value = _state.value.copy(titleProgress = done to total)
            val pages = (2..total).chunked(4).flatMap { chunk ->
                chunk.map { page -> async { repository.catalog(filter, page, TITLE_PAGE, compact = true) } }.awaitAll()
                    .also { done += it.size; _state.value = _state.value.copy(titleProgress = done to total) }
            }
            val failure = pages.filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (failure != null) {
                _state.value = _state.value.copy(loading = false, titleProgress = null, error = failure.message); return@launch
            }
            val all = (listOf(first) + pages).flatMap { (it as ApiResult.Success).value.releases }
                .distinctBy { it.id }.sortedByTitle()
            titleCache[filter] = all
            _state.value = _state.value.copy(releases = all, page = 1, totalPages = 1, loading = false, titleProgress = null)
        }
    }

    /** "I'm feeling lucky": a random release id, or null when the API fails. */
    fun random(onResult: (Int) -> Unit) = viewModelScope.launch {
        (repository.randomRelease() as? ApiResult.Success)?.value?.id?.let(onResult)
    }

    fun loadMore() {
        val current = _state.value
        if (current.titleSorted || !current.canLoadMore) return
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

    private companion object {
        const val TITLE_PAGE = 50 // API maximum
    }
}
