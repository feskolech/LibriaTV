package ru.feskolech.libriatv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.data.repo.AuthRepository
import ru.feskolech.libriatv.data.repo.AuthState
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.ScheduleItem

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(
        val latest: List<Release>,
        val today: List<ScheduleItem>,
        val tomorrow: List<ScheduleItem>,
        val favorites: List<Release>,
        val favoriteIds: Set<Int>,
        val isAuthorized: Boolean,
        val recommended: List<Release> = emptyList(),
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state
    private var lastRefresh = 0L
    private var loading = false

    init {
        refresh(force = true)
        viewModelScope.launch {
            auth.state.collect { authState ->
                val current = _state.value
                if (current is HomeUiState.Content &&
                    (authState is AuthState.Authorized) != current.isAuthorized) refresh(force = true)
            }
        }
    }

    fun refresh(force: Boolean = false) {
        if (loading || (!force && System.currentTimeMillis() - lastRefresh < 300_000L)) return
        loading = true
        viewModelScope.launch {
            if (_state.value !is HomeUiState.Content) _state.value = HomeUiState.Loading
            val latest = async { repository.latest() }
            val schedule = async { repository.currentSchedule() }
            // Optional row: a failure here must not break the whole home screen.
            val recommended = async { (repository.recommended() as? ApiResult.Success)?.value.orEmpty() }
            val latestResult = latest.await()
            val scheduleResult = schedule.await()
            if (latestResult is ApiResult.Success && scheduleResult is ApiResult.Success) {
                val authorized = auth.state.value is AuthState.Authorized
                val favorites = if (authorized) repository.favoriteReleases() else ApiResult.Success(emptyList())
                val favoriteList = (favorites as? ApiResult.Success)?.value.orEmpty()
                _state.value = HomeUiState.Content(
                    latestResult.value.sortedByDescending { it.freshAt },
                    scheduleResult.value.today, scheduleResult.value.tomorrow,
                    favoriteList, favoriteList.mapTo(mutableSetOf()) { it.id }, authorized,
                    recommended = recommended.await(),
                )
                lastRefresh = System.currentTimeMillis()
            } else {
                val error = (latestResult as? ApiResult.Failure)?.message
                    ?: (scheduleResult as? ApiResult.Failure)?.message.orEmpty()
                if (_state.value !is HomeUiState.Content) _state.value = HomeUiState.Error(error)
            }
            loading = false
        }
    }
}
