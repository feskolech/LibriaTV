package ru.feskolech.libriatv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.data.repo.SearchHistoryStore
import ru.feskolech.libriatv.domain.Release

sealed interface SearchResults {
    /** Query too short — the screen shows search history instead. */
    data object Idle : SearchResults
    data object Loading : SearchResults
    /** [releases]: titles matching the query; [similar]: the API's looser matches, shown below. */
    data class Content(val query: String, val releases: List<Release>, val similar: List<Release> = emptyList()) : SearchResults
    data class Error(val message: String) : SearchResults
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val history: SearchHistoryStore,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<SearchResults>(SearchResults.Idle)
    val results: StateFlow<SearchResults> = _results

    val recent: StateFlow<List<String>> = history.queries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            // collectLatest: a newer query cancels the one still in flight instead of waiting for it.
            _query.map(String::trim).distinctUntilChanged().debounce(DEBOUNCE_MS).collectLatest(::search)
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.trim().length < MIN_LENGTH) _results.value = SearchResults.Idle
    }

    /** Explicit search (IME action, voice, history item): no debounce and remembered in history. */
    fun submit(value: String = _query.value) {
        _query.value = value
        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            // Same text already searched by the debounced flow and shown: just remember it.
            val shown = (_results.value as? SearchResults.Content)?.query
            if (shown != value.trim()) search(value.trim())
            history.add(value)
        }
    }
    private var submitJob: kotlinx.coroutines.Job? = null

    /** Opening a result counts as a successful query worth remembering. */
    fun remember() { viewModelScope.launch { history.add(_query.value) } }

    fun clearHistory() { viewModelScope.launch { history.clear() } }

    private suspend fun search(query: String) {
        if (query.length < MIN_LENGTH) { _results.value = SearchResults.Idle; return }
        _results.value = SearchResults.Loading
        val result = repository.searchSplit(query)
        if (_query.value.trim() != query) return // a newer query is already on its way
        _results.value = when (result) {
            // Nothing matched exactly (typo, partial word): show the API's results as they are.
            is ApiResult.Success -> if (result.value.first.isEmpty()) SearchResults.Content(query, result.value.second)
                else SearchResults.Content(query, result.value.first, result.value.second)
            is ApiResult.Failure -> SearchResults.Error(result.message)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
        const val MIN_LENGTH = 2
    }
}
