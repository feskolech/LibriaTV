package ru.feskolech.libriatv.ui.torrents

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
import ru.feskolech.libriatv.domain.Torrent
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.data.repo.ProgressRepository
import ru.feskolech.libriatv.data.repo.PlaybackStore
import ru.feskolech.libriatv.data.repo.TorrServeClient
import ru.feskolech.libriatv.data.repo.TorrServeListing
import ru.feskolech.libriatv.data.repo.TorrServeFile

sealed interface TorrentsUiState {
    data object Loading : TorrentsUiState
    data class Content(
        val torrents: List<Torrent>, val listing: TorrServeListing? = null,
        val watched: Set<String> = emptySet(), val watchedNumbers: Set<Int> = emptySet(), val busy: Boolean = false,
        val error: Boolean = false, val hint: Boolean = false,
        val markChoices: List<Episode>? = null,
    ) : TorrentsUiState
    data class Error(val message: String) : TorrentsUiState
}

@HiltViewModel
class TorrentsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ApiRepository,
    private val progress: ProgressRepository,
    private val playback: PlaybackStore,
    private val torrServe: TorrServeClient,
) : ViewModel() {
    private val releaseId: Int = checkNotNull(savedState.get<String>("releaseId")).toInt()
    private val _state = MutableStateFlow<TorrentsUiState>(TorrentsUiState.Loading)
    val state: StateFlow<TorrentsUiState> = _state

    /** Magnet shown as a QR code when no app on the device can open it. */
    private val _qrMagnet = MutableStateFlow<String?>(null)
    val qrMagnet: StateFlow<String?> = _qrMagnet
    private var episodes: List<Episode> = emptyList()
    private var release: Release? = null
    private var activeTorrent: Torrent? = null
    private var hintShown = false

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = TorrentsUiState.Loading
        release = (repository.release(releaseId.toString()) as? ApiResult.Success)?.value
        episodes = release?.episodes.orEmpty()
        _state.value = when (val result = repository.torrents(releaseId)) {
            is ApiResult.Failure -> TorrentsUiState.Error(result.message)
            // Best quality first, then most seeded.
            is ApiResult.Success -> TorrentsUiState.Content(result.value.sortedWith(
                compareByDescending<Torrent> { it.quality?.filter(Char::isDigit)?.toIntOrNull() ?: 0 }
                    .thenByDescending { it.seeders ?: 0 },
            ))
        }
    }

    fun showQr(magnet: String?) { _qrMagnet.value = magnet }

    fun openTorrent(torrent: Torrent, fallback: (String) -> Boolean) = viewModelScope.launch {
        val magnet = torrent.magnet ?: return@launch
        activeTorrent = torrent
        update { it.copy(busy = true, error = false, hint = false) }
        if (!torrServe.available()) {
            val launched = fallback(magnet)
            update { it.copy(busy = false, hint = !hintShown) }
            hintShown = true
            if (launched) promptForTorrent(torrent)
            return@launch
        }
        runCatching {
            val listing = torrServe.addAndList(magnet)
            val watched = release?.let { progress.releaseProgress(it).filterValues { value -> value.watched }.keys }.orEmpty()
            update { it.copy(listing = listing, watched = watched,
                watchedNumbers = episodes.filter { episode -> episode.id in watched }.mapNotNull { episode -> episode.ordinal?.toInt() }.toSet(), busy = false) }
        }.onFailure { update { it.copy(busy = false, error = true) } }
    }

    fun closeListing() { update { it.copy(listing = null, markChoices = null, error = false) } }

    fun selectFile(file: TorrServeFile, open: (String) -> Boolean) = viewModelScope.launch {
        val listing = (_state.value as? TorrentsUiState.Content)?.listing ?: return@launch
        if (!open(torrServe.streamUrl(listing.hash, file))) {
            update { it.copy(error = true) }
            return@launch
        }
        closeListing()
        val numbers = listing.files.mapNotNull { it.episode }.toSet()
        promptForTorrent(activeTorrent, numbers)
    }

    private suspend fun promptForTorrent(torrent: Torrent?, listedEpisodes: Set<Int>? = null) {
        if (torrent == null || playback.torrentPrompted(torrent.id)) return
        val matching = if (listedEpisodes != null) episodes.filter { it.ordinal?.toInt() in listedEpisodes }
            else episodes.filter { episode -> torrent.episodes?.let { range ->
                val numbers = Regex("\\d+").findAll(range).map { it.value.toInt() }.toList()
                when (numbers.size) { 1 -> episode.ordinal?.toInt() == numbers[0]
                    else -> numbers.size >= 2 && episode.ordinal?.toInt() in numbers[0]..numbers[1] }
            } ?: true }
        if (matching.size == 1) {
            progress.markWatched(matching.single(), releaseId)
            playback.setTorrentPrompted(torrent.id)
            update { it.copy(watched = it.watched + matching.single().id) }
        } else if (matching.isNotEmpty()) {
            update { it.copy(markChoices = matching) }
        }
    }

    fun mark(selected: Set<String>) = viewModelScope.launch {
        val choices = (_state.value as? TorrentsUiState.Content)?.markChoices.orEmpty()
        choices.filter { it.id in selected }.forEach { progress.markWatched(it, releaseId) }
        activeTorrent?.let { playback.setTorrentPrompted(it.id) }
        update { it.copy(markChoices = null, watched = it.watched + selected) }
    }

    private fun update(block: (TorrentsUiState.Content) -> TorrentsUiState.Content) {
        (_state.value as? TorrentsUiState.Content)?.let { _state.value = block(it) }
    }
}
