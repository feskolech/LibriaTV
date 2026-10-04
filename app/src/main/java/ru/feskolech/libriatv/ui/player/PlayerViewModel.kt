package ru.feskolech.libriatv.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.data.repo.PlaybackStore
import ru.feskolech.libriatv.data.repo.TokenStore
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.Skip

enum class PlayerPanel { Hidden, Controls, Episodes }
data class PlayerContent(
    val release: Release, val episode: Episode, val quality: Int,
    val positionMs: Long = 0, val durationMs: Long = 0, val playing: Boolean = false,
    val panel: PlayerPanel = PlayerPanel.Hidden, val skip: Skip? = null,
    val skipOpening: Boolean = false, val nextCountdown: Int? = null,
    val autoSkip: Boolean = false, val error: String? = null,
    val buffering: Boolean = true, val speed: Float = 1f,
    /** Frame rate of the playing video, or null if the stream does not declare it. */
    val frameRate: Float? = null, val frameRateMatch: Boolean = true, val nightMode: Boolean = false,
)
sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Content(val value: PlayerContent) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext context: Context,
    private val repository: ApiRepository,
    private val store: PlaybackStore,
    private val tokenStore: TokenStore,
) : ViewModel() {
    private val releaseId: String = checkNotNull(savedState["id"])
    private val initialEpisodeId: String = checkNotNull(savedState["episodeId"])
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state
    /** Auto-skip each segment (opening, ending) at most once per episode, so seeking back into it is respected. */
    private val autoSkipped = mutableSetOf<Skip>()
    private var lastSync = 0L
    private val nightAudio = NightAudio()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { update { it.copy(playing = isPlaying) } }
            override fun onPlaybackStateChanged(playbackState: Int) {
                update { it.copy(buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE) }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val rate = player.videoFormat?.frameRate?.takeIf { it > 0f }
                update { it.copy(frameRate = rate) }
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                val night = (_state.value as? PlayerUiState.Content)?.value?.nightMode ?: false
                nightAudio.apply(audioSessionId, night)
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                update { it.copy(error = error.message ?: "Playback error", panel = PlayerPanel.Controls) }
            }
        })
        load()
        viewModelScope.launch {
            while (true) { delay(1000); tick() }
        }
    }

    fun load() = viewModelScope.launch {
        _state.value = PlayerUiState.Loading
        when (val result = repository.release(releaseId)) {
            is ApiResult.Failure -> _state.value = PlayerUiState.Error(result.message)
            is ApiResult.Success -> {
                val episode = result.value.episodes.firstOrNull { it.id == initialEpisodeId }
                    ?: result.value.episodes.firstOrNull()
                if (episode == null) _state.value = PlayerUiState.Error("No episodes")
                else {
                    val speed = store.speed()
                    val night = store.nightMode()
                    _state.value = PlayerUiState.Content(PlayerContent(result.value, episode, store.quality(), autoSkip = store.autoSkip(),
                        speed = speed, frameRateMatch = store.frameRateMatch(), nightMode = night))
                    player.setPlaybackSpeed(speed)
                    nightAudio.apply(player.audioSessionId, night)
                    prepare(episode, resume = true)
                }
            }
        }
    }

    private fun update(block: (PlayerContent) -> PlayerContent) {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return
        _state.value = PlayerUiState.Content(block(current))
    }

    private suspend fun prepare(episode: Episode, resume: Boolean) {
        val state = (_state.value as? PlayerUiState.Content)?.value ?: return
        val (quality, url) = listOf(1080 to episode.hls1080, 720 to episode.hls720, 480 to episode.hls480)
            .filter { !it.second.isNullOrBlank() }
            .let { options -> options.firstOrNull { it.first == state.quality } ?: options.firstOrNull() }
            ?: run { update { it.copy(error = "No video stream") }; return }
        update { it.copy(episode = episode, quality = quality, positionMs = 0, durationMs = 0, skip = null, nextCountdown = null, error = null) }
        autoSkipped.clear()
        player.setMediaItem(MediaItem.fromUri(url!!))
        player.prepare()
        val position = if (resume) store.progress(episode.id)?.positionMs ?: 0 else 0
        player.seekTo(position)
        player.playWhenReady = true
    }

    fun playEpisode(id: String) = viewModelScope.launch {
        saveProgress()
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return@launch
        current.release.episodes.firstOrNull { it.id == id }?.let { prepare(it, resume = true) }
        update { it.copy(panel = PlayerPanel.Hidden) }
    }

    fun changeQuality(quality: Int) = viewModelScope.launch {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return@launch
        if (current.quality == quality) return@launch
        val position = player.currentPosition
        store.setQuality(quality)
        update { it.copy(quality = quality) }
        prepare(current.episode, resume = false)
        player.seekTo(position)
        update { it.copy(panel = PlayerPanel.Controls) }
    }

    fun toggleAutoSkip() = viewModelScope.launch {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return@launch
        store.setAutoSkip(!current.autoSkip)
        update { it.copy(autoSkip = !current.autoSkip) }
    }

    fun togglePause() { if (player.isPlaying) player.pause() else player.play() }

    fun cycleSpeed() = viewModelScope.launch {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return@launch
        val next = SPEEDS[(SPEEDS.indexOf(current.speed) + 1) % SPEEDS.size]
        player.setPlaybackSpeed(next)
        store.setSpeed(next)
        update { it.copy(speed = next) }
    }

    fun toggleNightMode() = viewModelScope.launch {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return@launch
        val night = !current.nightMode
        store.setNightMode(night)
        nightAudio.apply(player.audioSessionId, night)
        update { it.copy(nightMode = night) }
    }

    fun previousEpisode() {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return
        val episodes = current.release.episodes.sortedBy { it.ordinal ?: 0.0 }
        episodes.getOrNull(episodes.indexOfFirst { it.id == current.episode.id } - 1)?.let { playEpisode(it.id) }
    }

    /** Number keys on remotes that have them: jump to episode N. */
    fun playEpisodeNumber(number: Int) {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return
        current.release.episodes.firstOrNull { it.ordinal?.toInt() == number }?.let { playEpisode(it.id) }
    }
    fun seek(direction: Int, repeat: Int = 0) {
        val seconds = when { repeat >= 8 -> 60; repeat >= 3 -> 30; else -> 10 }
        player.seekTo((player.currentPosition + direction * seconds * 1000L).coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        update { it.copy(panel = PlayerPanel.Hidden, nextCountdown = null) }
    }
    fun showPanel(panel: PlayerPanel) { update { it.copy(panel = panel) } }
    fun hidePanel(): Boolean {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return false
        if (current.panel == PlayerPanel.Hidden) return false
        update { it.copy(panel = PlayerPanel.Hidden) }
        return true
    }
    fun skip() {
        val skip = (_state.value as? PlayerUiState.Content)?.value?.skip ?: return
        player.seekTo(((skip.stop ?: return) * 1000).toLong())
        update { it.copy(skip = null) }
    }

    private fun tick() {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return
        val position = player.currentPosition.coerceAtLeast(0)
        val duration = player.duration.coerceAtLeast(0)
        val seconds = position / 1000.0
        val opening = current.episode.opening?.takeIf { it.start != null && it.stop != null && seconds >= it.start && seconds < it.stop }
        val ending = current.episode.ending?.takeIf { it.start != null && it.stop != null && seconds >= it.start && seconds < it.stop }
        val skip = opening ?: ending
        if (skip != null && current.autoSkip && autoSkipped.add(skip)) {
            player.seekTo((skip.stop!! * 1000).toLong())
        }
        val remaining = if (duration > 0) (duration - position) / 1000 else Long.MAX_VALUE
        val next = nextEpisode(current)
        val countdown = if (remaining in 1..8 && next != null) remaining.toInt() else null
        update { it.copy(positionMs = position, durationMs = duration, skip = if (current.autoSkip) null else skip,
            skipOpening = opening != null, nextCountdown = countdown) }
        if (duration > 0 && remaining <= 0 && next != null) playEpisode(next.id)
        if (position - lastSync >= 15_000 || position < lastSync) {
            lastSync = position
            viewModelScope.launch { saveProgress() }
        }
    }

    private fun nextEpisode(current: PlayerContent): Episode? {
        val episodes = current.release.episodes.sortedBy { it.ordinal ?: 0.0 }
        return episodes.getOrNull(episodes.indexOfFirst { it.id == current.episode.id } + 1)
    }

    fun nextEpisode() {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return
        nextEpisode(current)?.let { playEpisode(it.id) }
    }

    fun pauseAndSave() { player.pause(); viewModelScope.launch { saveProgress() } }
    fun close(onSaved: () -> Unit) = viewModelScope.launch { saveProgress(); onSaved() }

    private suspend fun saveProgress() {
        val current = (_state.value as? PlayerUiState.Content)?.value ?: return
        val position = player.currentPosition.coerceAtLeast(0)
        val duration = player.duration.coerceAtLeast(0)
        store.save(current.episode.id, position, duration)
        if (!tokenStore.get().isNullOrBlank()) {
            repository.saveTimecode(current.episode.id, position / 1000.0,
                duration > 0 && position >= duration * 0.9)
        }
    }

    override fun onCleared() { nightAudio.release(); player.release(); super.onCleared() }

    private companion object {
        val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
    }
}
