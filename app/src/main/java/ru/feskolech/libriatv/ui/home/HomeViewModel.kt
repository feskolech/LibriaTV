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
import ru.feskolech.libriatv.data.repo.ProgressRepository
import ru.feskolech.libriatv.data.repo.ContinueItem
import ru.feskolech.libriatv.data.repo.FavoritesRepository
import ru.feskolech.libriatv.data.repo.FavoriteEpisodeStore
import ru.feskolech.libriatv.data.repo.NewFavoriteEpisode
import ru.feskolech.libriatv.data.repo.compareFavoriteEpisodes
import ru.feskolech.libriatv.data.repo.SettingsStore

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
        val continueItems: List<ContinueItem> = emptyList(),
        val newEpisodes: List<NewFavoriteEpisode> = emptyList(),
        val showEpisodeDialog: Boolean = false,
        val videoPreviewEnabled: Boolean = false,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val auth: AuthRepository,
    private val progress: ProgressRepository,
    private val favoritesRepository: FavoritesRepository,
    private val episodeStore: FavoriteEpisodeStore,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state
    private var lastRefresh = 0L
    private var loading = false
    private var checkedUserId: Int? = null

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
            val continueItems = async { progress.continueItems() }
            val videoPreviewEnabled = async { settings.homeVideoPreview() }
            val latestResult = latest.await()
            val scheduleResult = schedule.await()
            if (latestResult is ApiResult.Success && scheduleResult is ApiResult.Success) {
                val authorized = auth.state.value is AuthState.Authorized
                val favorites = if (authorized) favoritesRepository.allReleases() else ApiResult.Success(emptyList())
                val favoriteList = (favorites as? ApiResult.Success)?.value.orEmpty()
                if (authorized) favoritesRepository.refreshIds()
                val previous = _state.value as? HomeUiState.Content
                var newEpisodes = previous?.newEpisodes.orEmpty()
                var showDialog = previous?.showEpisodeDialog ?: false
                val userId = (auth.state.value as? AuthState.Authorized)?.user?.id
                if (checkedUserId != userId && userId != null && favorites is ApiResult.Success) {
                    newEpisodes = compareFavoriteEpisodes(episodeStore.previous(userId), favoriteList)
                    episodeStore.save(userId, favoriteList)
                    showDialog = newEpisodes.isNotEmpty()
                    checkedUserId = userId
                }
                if (userId == null) checkedUserId = null
                _state.value = HomeUiState.Content(
                    latestResult.value.sortedByDescending { it.freshAt },
                    scheduleResult.value.today, scheduleResult.value.tomorrow,
                    favoriteList, favoritesRepository.ids.value, authorized,
                    recommended = recommended.await(),
                    videoPreviewEnabled = videoPreviewEnabled.await(),
                    continueItems = continueItems.await(),
                    newEpisodes = newEpisodes, showEpisodeDialog = showDialog,
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

    fun dismissEpisodeDialog() {
        val content = _state.value as? HomeUiState.Content ?: return
        _state.value = content.copy(showEpisodeDialog = false)
    }
}
