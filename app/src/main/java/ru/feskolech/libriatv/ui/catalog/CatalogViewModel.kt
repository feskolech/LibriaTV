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
import ru.feskolech.libriatv.data.repo.SavedCatalogFilter
import ru.feskolech.libriatv.data.repo.SavedFiltersStore
import kotlinx.coroutines.delay
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
    /** Filters the viewer kept as chips of their own (the new catalog). */
    val saved: List<SavedCatalogFilter> = emptyList(),
) {
    val canLoadMore: Boolean get() = !loading && page < totalPages
}

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val messages: ru.feskolech.libriatv.ui.components.UserMessages,
    private val savedFilters: SavedFiltersStore,
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state
    private var pageJob: Job? = null

    init {
        loadReferences()
        reload()
        viewModelScope.launch { savedFilters.filters.collect { _state.value = _state.value.copy(saved = it) } }
    }

    /**
     * A quick pick, a saved filter or a change in the filter panel. Picks switch as the focus moves along
     * them, so the request waits a moment: passing over a chip on the way to the next one asks nothing.
     */
    fun pick(filter: CatalogFilter, titleSorted: Boolean = false) {
        val current = _state.value
        if (filter == current.filter && titleSorted == current.titleSorted) return
        _state.value = current.copy(filter = filter, titleSorted = titleSorted)
        reload(debounceMs = PICK_DELAY_MS)
    }

    fun saveCurrent(name: String) = viewModelScope.launch {
        savedFilters.add(SavedCatalogFilter(name, _state.value.filter, _state.value.titleSorted))
    }

    fun removeSaved(saved: SavedCatalogFilter) = viewModelScope.launch { savedFilters.remove(saved) }

    /**
     * "I'm feeling lucky" within what is shown: a random title of the current filter (the whole catalog
     * when nothing is filtered). Asking for one title per page makes the page count the number of titles.
     */
    fun lucky(onResult: (Int) -> Unit) = viewModelScope.launch {
        val filter = _state.value.filter
        val id = if (filter.activeCount == 0) (repository.randomRelease() as? ApiResult.Success)?.value?.id else {
            val total = (repository.catalog(filter, 1, limit = 1, compact = true) as? ApiResult.Success)?.value?.totalPages
            if (total == 0) { messages.show(ru.feskolech.libriatv.R.string.catalog_empty); return@launch }
            total?.let { n ->
                (repository.catalog(filter, kotlin.random.Random.nextInt(1, n + 1), limit = 1, compact = true)
                    as? ApiResult.Success)?.value?.releases?.firstOrNull()?.id
            }
        }
        if (id != null) onResult(id) else messages.show(ru.feskolech.libriatv.R.string.error_network_short)
    }

    fun loadReferences() = viewModelScope.launch {
        when (val result = repository.catalogReferences()) {
            is ApiResult.Success -> _state.value = _state.value.copy(
                references = result.value,
                filter = _state.value.filter.let { it.copy(sorting = it.sorting ?: result.value.sorting.firstOrNull()?.id) },
            )
            // The grid still works with defaults, but say why the filters are missing.
            is ApiResult.Failure -> messages.show(ru.feskolech.libriatv.R.string.error_filters_unavailable)
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

    fun reload(debounceMs: Long = 0) {
        pageJob?.cancel()
        _state.value = _state.value.copy(releases = emptyList(), page = 0, totalPages = 1, loading = debounceMs > 0,
            error = null, titleProgress = null)
        if (debounceMs > 0) pageJob = viewModelScope.launch {
            delay(debounceMs)
            _state.value = _state.value.copy(loading = false)
            if (_state.value.titleSorted) loadAllByTitle() else loadMore()
        } else if (_state.value.titleSorted) loadAllByTitle() else loadMore()
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
        val id = (repository.randomRelease() as? ApiResult.Success)?.value?.id
        if (id != null) onResult(id) else messages.show(ru.feskolech.libriatv.R.string.error_network_short)
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
        const val PICK_DELAY_MS = 300L
    }
}
