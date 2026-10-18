package ru.feskolech.libriatv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.data.repo.AuthRepository
import ru.feskolech.libriatv.data.repo.AuthState
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.CatalogFilter
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
        /** Next catalog page for the endless "new episodes" row; null when the end was reached. */
        val latestNextPage: Int? = 2,
        /** "New episodes" is being asked again (see [HomeViewModel.refreshLatest]). */
        val refreshingLatest: Boolean = false,
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
    private val newEpisodesChannel: ru.feskolech.libriatv.data.repo.NewEpisodesChannel,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state
    private var lastRefresh = 0L
    private var loading = false
    /** When favorites were last compared for new episodes, and for whom (see [checkFavorites]). */
    private var favoritesCheckedAt = 0L
    private var favoritesCheckedFor: Int? = null
    private val favoritesCheck = kotlinx.coroutines.sync.Mutex()

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
        val started = System.currentTimeMillis()
        viewModelScope.launch {
            if (_state.value !is HomeUiState.Content) _state.value = HomeUiState.Loading
            val latest = async { repository.latest(LATEST_FIRST_PAGE) }
            val schedule = async { repository.currentSchedule() }
            // Optional row: a failure here must not break the whole home screen.
            val recommended = async { (repository.recommended() as? ApiResult.Success)?.value.orEmpty() }
            val videoPreviewEnabled = async { settings.homeVideoPreview() }
            val latestResult = latest.await()
            val scheduleResult = schedule.await()
            if (latestResult is ApiResult.Success && scheduleResult is ApiResult.Success) {
                val authorized = auth.state.value is AuthState.Authorized
                val previous = _state.value as? HomeUiState.Content
                // Show the screen right away; the full favorites list (only needed for the
                // "new in favorites" notice) is fetched afterwards in the background.
                _state.value = HomeUiState.Content(
                    latestResult.value.sortedByDescending { it.freshAt },
                    scheduleResult.value.today, scheduleResult.value.tomorrow,
                    previous?.favorites.orEmpty(), favoritesRepository.ids.value, authorized,
                    recommended = recommended.await(),
                    videoPreviewEnabled = videoPreviewEnabled.await(),
                    continueItems = previous?.continueItems.orEmpty(),
                    newEpisodes = previous?.newEpisodes.orEmpty(),
                    showEpisodeDialog = previous?.showEpisodeDialog ?: false,
                    latestNextPage = if (latestResult.value.size < LATEST_FIRST_PAGE) null else 2,
                )
                android.util.Log.i("LibriaNet", "home shown after ${System.currentTimeMillis() - started} ms")
                // "Continue watching" may need many lookups: it fills in after the screen is shown.
                refreshContinue()
                if (authorized) viewModelScope.launch { checkFavorites() }
                lastRefresh = System.currentTimeMillis()
            } else {
                val error = (latestResult as? ApiResult.Failure)?.message
                    ?: (scheduleResult as? ApiResult.Failure)?.message.orEmpty()
                if (_state.value !is HomeUiState.Content) _state.value = HomeUiState.Error(error)
            }
            loading = false
        }
    }

    private var latestJob: kotlinx.coroutines.Job? = null

    /**
     * Back on Home: asks "New episodes" again (one request) every time, so a fresh episode shows up at once
     * instead of after the full 5-minute refresh. The row stays as it is meanwhile; cards already appended
     * by the endless row are kept after the fresh ones.
     */
    fun refreshLatest() {
        if (loading || latestJob?.isActive == true || _state.value !is HomeUiState.Content) return
        latestJob = viewModelScope.launch {
            (_state.value as? HomeUiState.Content)?.let { _state.value = it.copy(refreshingLatest = true) }
            val result = repository.latest(LATEST_FIRST_PAGE)
            val current = _state.value as? HomeUiState.Content ?: return@launch
            _state.value = if (result is ApiResult.Success) {
                val fresh = result.value.sortedByDescending { it.freshAt }
                val ids = fresh.mapTo(HashSet()) { it.id }
                current.copy(latest = fresh + current.latest.filter { it.id !in ids }, refreshingLatest = false)
            } else current.copy(refreshingLatest = false)
            if (auth.state.value is AuthState.Authorized) checkFavorites()
        }
    }

    private var continueJob: kotlinx.coroutines.Job? = null

    /** Re-reads "Continue watching" only (cheap): used when coming back to Home from a player. */
    fun refreshContinue() {
        continueJob?.cancel()
        continueJob = viewModelScope.launch {
            val items = progress.continueItems()
            val current = _state.value as? HomeUiState.Content ?: return@launch
            if (current.continueItems != items) _state.value = current.copy(continueItems = items)
        }
    }

    private var loadingMoreLatest = false

    /**
     * New episodes in favorites since the last check: on every visit to Home, at most once a minute (it
     * fetches the whole favorites list). It used to run once per app start, as Home lives as long as the
     * app does, so an episode that came out while the app stayed open was never announced. A notice not
     * dismissed yet keeps its episodes and gains the new ones.
     */
    private suspend fun checkFavorites(): Unit = favoritesCheck.withLock {
        val userId = (auth.state.value as? AuthState.Authorized)?.user?.id ?: run { favoritesCheckedFor = null; return }
        val now = System.currentTimeMillis()
        if (favoritesCheckedFor == userId && now - favoritesCheckedAt < FAVORITES_CHECK_INTERVAL_MS) return
        favoritesRepository.refreshIds()
        val favorites = favoritesRepository.allReleases() as? ApiResult.Success ?: return
        val newEpisodes = compareFavoriteEpisodes(episodeStore.previous(userId), favorites.value)
        episodeStore.save(userId, favorites.value)
        // The baseline just moved: everything is "seen", so the home-screen channel is emptied.
        newEpisodesChannel.publish(emptyList())
        favoritesCheckedFor = userId
        favoritesCheckedAt = now
        val current = _state.value as? HomeUiState.Content ?: return
        // Switched off in Settings: the baseline above still moves, so turning notices back on later does
        // not announce everything that came out meanwhile.
        if (!settings.newEpisodeNotices()) {
            _state.value = current.copy(favoriteIds = favoritesRepository.ids.value, newEpisodes = emptyList(), showEpisodeDialog = false)
            return
        }
        _state.value = if (newEpisodes.isEmpty()) current.copy(favoriteIds = favoritesRepository.ids.value)
            else current.copy(favoriteIds = favoritesRepository.ids.value, showEpisodeDialog = true,
                newEpisodes = mergeNewEpisodes(if (current.showEpisodeDialog) current.newEpisodes else emptyList(), newEpisodes))
    }

    /**
     * Endless "new episodes": `releases/latest` caps at 50 and has no paging, but the catalog sorted
     * by FRESH_AT_DESC returns the same order with pages, so page N (size 50) continues the list.
     * Catalog items carry no latest episode, so older cards show only the "hours ago" badge.
     */
    fun loadMoreLatest() {
        val content = _state.value as? HomeUiState.Content ?: return
        val page = content.latestNextPage ?: return
        if (loadingMoreLatest) return
        loadingMoreLatest = true
        viewModelScope.launch {
            val result = repository.catalog(CatalogFilter(sorting = "FRESH_AT_DESC"), page, LATEST_FIRST_PAGE,
                fields = "id,name,alias,poster,year,type,season,publish_day,fresh_at,description,genres")
            val current = _state.value as? HomeUiState.Content
            if (current != null && result is ApiResult.Success) {
                val known = current.latest.mapTo(HashSet()) { it.id }
                _state.value = current.copy(
                    latest = current.latest + result.value.releases.filter { it.id !in known },
                    latestNextPage = if (page < result.value.totalPages) page + 1 else null,
                )
            }
            loadingMoreLatest = false
        }
    }

    fun dismissEpisodeDialog() {
        val content = _state.value as? HomeUiState.Content ?: return
        _state.value = content.copy(showEpisodeDialog = false)
    }

    private companion object {
        const val LATEST_FIRST_PAGE = 50 // API maximum for releases/latest and the catalog
        const val FAVORITES_CHECK_INTERVAL_MS = 60_000L
    }
}

/** A notice still on screen plus a later check: one line per release, from its earliest new episode to the latest. */
internal fun mergeNewEpisodes(shown: List<NewFavoriteEpisode>, fresh: List<NewFavoriteEpisode>): List<NewFavoriteEpisode> {
    val byRelease = LinkedHashMap<Int, NewFavoriteEpisode>()
    (shown + fresh).forEach { item ->
        val old = byRelease[item.release.id]
        byRelease[item.release.id] = if (old == null) item
            else NewFavoriteEpisode(item.release, minOf(old.from, item.from), maxOf(old.to, item.to))
    }
    return byRelease.values.toList()
}
