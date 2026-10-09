package ru.feskolech.libriatv.ui.player

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.PreloadManagerListener
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.data.repo.PlaybackStore
import ru.feskolech.libriatv.data.repo.TokenStore
import ru.feskolech.libriatv.data.repo.WatchNextPublisher
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Skip

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext context: Context,
    private val repository: ApiRepository,
    private val store: PlaybackStore,
    private val tokenStore: TokenStore,
    private val watchNext: WatchNextPublisher,
    client: OkHttpClient,
    @ru.feskolech.libriatv.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {
    private val frameLoader = SeekFrameLoader(context, client.newBuilder().build())
    private val bufferingHint = BufferingHint()
    private var retryCount = 0
    private var sleepDeadline = 0L
    private var seekJob: Job? = null
    /** Pending silent retry after a playback error; cancelled when another episode is prepared. */
    private var retryJob: Job? = null
    private var seekHideJob: Job? = null
    private var ignoreBufferUntil = 0L
    private val releaseId: String = checkNotNull(savedState["id"])
    private val initialEpisodeId: String = checkNotNull(savedState["episodeId"])
    private val preloadBuilder = DefaultPreloadManager.Builder(context,
        TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {
            DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(10_000L)
        })
    val player: ExoPlayer = preloadBuilder.buildExoPlayer()
    private val preloadManager = preloadBuilder.build()
    private var queuedItem: MediaItem? = null
    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state
    /** The loaded episode's state, or null while loading or on an error. */
    private val content: PlayerContent? get() = (_state.value as? PlayerUiState.Content)?.value
    /** Auto-skip each segment (opening, ending) at most once per episode, so seeking back into it is respected. */
    private val autoSkipped = mutableSetOf<Skip>()
    private var lastSync = 0L
    private var progressSavedForTransition = false
    private val nightAudio = NightAudio()

    init {
        preloadManager.addListener(object : PreloadManagerListener {
            override fun onCompleted(mediaItem: MediaItem) {
                viewModelScope.launch {
                    val current = content ?: return@launch
                    if (queuedItem?.mediaId != mediaItem.mediaId ||
                        current.release.episodeAfter(current.episode.id)?.id != mediaItem.mediaId || player.hasNextMediaItem()) return@launch
                    preloadManager.getMediaSource(mediaItem)?.let { player.addMediaSource(it) }
                }
            }
        })
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) retryCount = 0
                update { it.copy(playing = isPlaying) }
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                update { it.copy(keepAwake = shouldKeepAwake()) }
                // With auto-next off the player pauses at the end of each episode instead of ending.
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) finishIfLastEpisode()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                update { it.copy(buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE,
                    keepAwake = shouldKeepAwake()) }
                val current = content ?: return
                val now = SystemClock.elapsedRealtime()
                if (playbackState == Player.STATE_BUFFERING && player.playWhenReady && now >= ignoreBufferUntil)
                    bufferingHint.start(now)
                else if (playbackState == Player.STATE_READY) {
                    val lower = lowerQuality(current.episode, current.quality)
                    if (bufferingHint.finish(now, current.quality, lower != null))
                        update { it.copy(qualityHint = lower) }
                } else bufferingHint.interrupt()
                if (playbackState == Player.STATE_ENDED && current.sleepTimer == SleepTimer.AfterEpisode) triggerSleep()
                if (playbackState == Player.STATE_ENDED) finishIfLastEpisode()
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val rate = player.videoFormat?.frameRate?.takeIf { it > 0f }
                update { it.copy(frameRate = rate) }
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                val night = content?.nightMode ?: false
                nightAudio.apply(audioSessionId, night)
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Network hiccups are common on TV boxes: retry silently (2, 4, 8 s) from the same
                // position before bothering the viewer with an error.
                if (retryCount < RETRY_DELAYS_MS.size) {
                    val delayMs = RETRY_DELAYS_MS[retryCount++]
                    val position = player.currentPosition
                    update { it.copy(buffering = true) }
                    retryJob?.cancel()
                    retryJob = viewModelScope.launch {
                        delay(delayMs)
                        player.prepare()
                        player.seekTo(position)
                        player.playWhenReady = true
                    }
                } else {
                    update { it.copy(error = PLAYBACK_FAILED, buffering = false, panel = PlayerPanel.Controls) }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val current = content ?: return
                val next = current.release.episodes.firstOrNull { it.id == mediaItem?.mediaId } ?: return
                if (next.id == current.episode.id) return
                if (!progressSavedForTransition) {
                    viewModelScope.launch { saveProgress(current, current.positionMs, current.durationMs) }
                }
                progressSavedForTransition = false
                autoSkipped.clear()
                bufferingHint.clear()
                clearSeekPreview()
                lastSync = 0
                queuedItem = null
                update { it.copy(episode = next, quality = streamFor(next, it.quality)?.first ?: it.quality,
                    positionMs = 0, durationMs = 0,
                    skip = null, skipOpening = false, nextCountdown = null, error = null,
                    panel = PlayerPanel.Hidden, qualityHint = null) }
                viewModelScope.launch {
                    delay(5_000)
                    if (player.currentMediaItemIndex > 0 && player.currentMediaItem?.mediaId == next.id) {
                        val old = player.getMediaItemAt(0)
                        player.removeMediaItems(0, player.currentMediaItemIndex)
                        preloadManager.remove(old)
                    }
                }
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
                    val autoNext = store.autoNext()
                    _state.value = PlayerUiState.Content(PlayerContent(result.value, episode, store.quality(), autoSkipOpening = store.autoSkipOpening(), autoSkipEnding = store.autoSkipEnding(),
                        speed = speed, frameRateMatch = store.frameRateMatch(), nightMode = night, autoNext = autoNext))
                    player.setPauseAtEndOfMediaItems(!autoNext)
                    player.setPlaybackSpeed(speed)
                    nightAudio.apply(player.audioSessionId, night)
                    prepare(episode, resume = true)
                }
            }
        }
    }

    private fun update(block: (PlayerContent) -> PlayerContent) {
        val current = content ?: return
        _state.value = PlayerUiState.Content(block(current))
    }

    /** The last episode played to the end: there is nothing more to watch, the screen goes back to the release. */
    private fun finishIfLastEpisode() {
        val current = content ?: return
        if (current.release.isLastEpisode(current.episode.id)) update { it.copy(finished = true) }
    }

    /** Not [Player.isPlaying]: that drops during buffering, and clearing the flag then could sleep the TV at once. */
    private fun shouldKeepAwake() = player.playWhenReady && player.playbackState != Player.STATE_ENDED

    private suspend fun prepare(episode: Episode, resume: Boolean) {
        val state = content ?: return
        val (quality, url) = streamFor(episode, state.quality)
            ?: run { update { it.copy(error = "No video stream") }; return }
        retryJob?.cancel()
        update { it.copy(episode = episode, quality = quality, positionMs = 0, durationMs = 0, skip = null, nextCountdown = null, error = null) }
        autoSkipped.clear()
        bufferingHint.clear()
        ignoreBufferUntil = SystemClock.elapsedRealtime() + 3_000
        clearSeekPreview()
        progressSavedForTransition = false
        queuedItem = null
        player.setMediaItem(mediaItem(episode, url))
        preloadManager.reset()
        player.prepare()
        val saved = if (resume) store.progress(episode.id) else null
        val position = resumePositionMs(saved?.positionMs, saved?.durationMs)
        player.seekTo(position)
        player.playWhenReady = true
        android.util.Log.i("EpisodePlayer", "playing: episode=${episode.id}")
    }

    /** [keepPanel]: switching from the prev/next buttons keeps the controls on screen. */
    fun playEpisode(id: String, keepPanel: Boolean = false) {
        val current = content ?: return
        if (id != current.episode.id && current.sleepTimer == SleepTimer.AfterEpisode) setSleepTimer(SleepTimer.Off)
        if (player.hasNextMediaItem() && player.getMediaItemAt(player.currentMediaItemIndex + 1).mediaId == id) {
            val position = player.currentPosition.coerceAtLeast(0)
            val duration = player.duration.coerceAtLeast(0)
            viewModelScope.launch { saveProgress(current, position, duration) }
            progressSavedForTransition = true
            player.seekToNextMediaItem()
            player.play()
            update { it.copy(panel = if (keepPanel) it.panel else PlayerPanel.Hidden) }
        } else viewModelScope.launch {
            saveProgress()
            current.release.episodes.firstOrNull { it.id == id }?.let { prepare(it, resume = true) }
            update { it.copy(panel = if (keepPanel) it.panel else PlayerPanel.Hidden) }
        }
    }

    /** "Retry" on the error panel: start over from the same position with a fresh retry budget. */
    fun retryPlayback() {
        retryCount = 0
        val position = player.currentPosition
        update { it.copy(error = null, buffering = true, panel = PlayerPanel.Hidden) }
        player.prepare()
        player.seekTo(position)
        player.playWhenReady = true
    }

    fun changeQuality(quality: Int) = viewModelScope.launch {
        val current = content ?: return@launch
        if (current.quality == quality) return@launch
        val position = player.currentPosition
        retryCount = 0
        update { it.copy(error = null) }
        store.setQuality(quality)
        update { it.copy(quality = quality) }
        prepare(current.episode, resume = false)
        player.seekTo(position)
        update { it.copy(qualityHint = null) }
    }

    fun toggleAutoSkipOpening() = viewModelScope.launch {
        val current = content ?: return@launch
        store.setAutoSkipOpening(!current.autoSkipOpening)
        update { it.copy(autoSkipOpening = !current.autoSkipOpening) }
    }

    fun toggleAutoSkipEnding() = viewModelScope.launch {
        val current = content ?: return@launch
        store.setAutoSkipEnding(!current.autoSkipEnding)
        update { it.copy(autoSkipEnding = !current.autoSkipEnding) }
    }

    fun togglePause() { if (player.isPlaying) player.pause() else player.play() }

    fun cycleSpeed() = viewModelScope.launch {
        val current = content ?: return@launch
        val next = SPEEDS[(SPEEDS.indexOf(current.speed) + 1) % SPEEDS.size]
        player.setPlaybackSpeed(next)
        store.setSpeed(next)
        update { it.copy(speed = next) }
    }

    fun setSpeed(value: Float) = viewModelScope.launch {
        player.setPlaybackSpeed(value)
        store.setSpeed(value)
        update { it.copy(speed = value) }
    }

    fun toggleNightMode() = viewModelScope.launch {
        val current = content ?: return@launch
        val night = !current.nightMode
        store.setNightMode(night)
        nightAudio.apply(player.audioSessionId, night)
        update { it.copy(nightMode = night) }
    }

    fun previousEpisode(keepPanel: Boolean = false) {
        val current = content ?: return
        current.release.episodeBefore(current.episode.id)?.let { playEpisode(it.id, keepPanel) }
    }

    /** Number keys on remotes that have them: jump to episode N. */
    fun playEpisodeNumber(number: Int) {
        val current = content ?: return
        current.release.episodeNumber(number)?.let { playEpisode(it.id) }
    }
    /** [keepPanel]: seeking from the progress bar keeps the controls open instead of hiding them. */
    fun seek(direction: Int, repeat: Int = 0, keepPanel: Boolean = false) {
        val seconds = seekStepSeconds(repeat)
        val base = content?.seekTargetMs ?: player.currentPosition
        val target = (base + direction * seconds * 1000L)
            .coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        bufferingHint.interrupt()
        ignoreBufferUntil = SystemClock.elapsedRealtime() + 3_000
        player.seekTo(target)
        update { it.copy(panel = if (keepPanel) it.panel else PlayerPanel.Hidden, nextCountdown = null,
            seekTargetMs = if (repeat > 0) target else null, seekFrame = null) }
        seekJob?.cancel()
        seekHideJob?.cancel()
        if (repeat > 0) {
            val episode = content?.episode ?: return
            seekJob = viewModelScope.launch {
                delay(250)
                val url = episode.hls480 ?: return@launch
                val bitmap = withTimeoutOrNull(1_000) { frameLoader.frame(episode.id, url, target) }
                if (bitmap != null) update { it.copy(seekFrame = bitmap) }
            }
            seekHideJob = viewModelScope.launch { delay(1_500); clearSeekPreview() }
        }
    }
    private fun clearSeekPreview() {
        seekJob?.cancel(); seekHideJob?.cancel()
        update { it.copy(seekTargetMs = null, seekFrame = null) }
    }

    fun setSleepTimer(timer: SleepTimer) {
        sleepDeadline = sleepDeadline(timer, SystemClock.elapsedRealtime())
        update { it.copy(sleepTimer = timer, sleepWarning = false) }
        if (timer == SleepTimer.AfterEpisode) {
            if (player.hasNextMediaItem()) player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
            queuedItem = null
            preloadManager.reset()
        }
        player.setPauseAtEndOfMediaItems(timer == SleepTimer.AfterEpisode ||
            !(content?.autoNext ?: true))
    }

    fun cancelSleepWarning() = setSleepTimer(SleepTimer.Off)

    private fun triggerSleep() {
        sleepDeadline = 0L
        player.pause()
        update { it.copy(sleepTimer = SleepTimer.Off, sleepWarning = false, nextCountdown = null) }
        viewModelScope.launch { saveProgress() }
    }

    fun dismissQualityHint() {
        bufferingHint.dismiss()
        update { it.copy(qualityHint = null) }
    }

    fun acceptQualityHint() {
        val quality = content?.qualityHint ?: return
        dismissQualityHint()
        changeQuality(quality)
    }

    fun showPanel(panel: PlayerPanel) { update { it.copy(panel = panel) } }
    fun hidePanel(): Boolean {
        val current = content ?: return false
        if (current.panel == PlayerPanel.Hidden) return false
        // Back from the settings menu or the episode list returns to the playback controls, not to
        // the bare video: both are reached from the controls' buttons.
        update { it.copy(panel = if (current.panel == PlayerPanel.Controls) PlayerPanel.Hidden else PlayerPanel.Controls) }
        return true
    }
    fun skip() {
        val skip = content?.skip ?: return
        player.seekTo(((skip.stop ?: return) * 1000).toLong())
        update { it.copy(skip = null) }
    }

    private fun tick() {
        val current = content ?: return
        if (sleepDeadline > 0) {
            val left = sleepDeadline - SystemClock.elapsedRealtime()
            if (left <= 0) { triggerSleep(); return }
            if (left <= 30_000 && !current.sleepWarning) update { it.copy(sleepWarning = true) }
        }
        val position = player.currentPosition.coerceAtLeast(0)
        val duration = player.duration.coerceAtLeast(0)
        val active = activeSkip(current.episode, position)
        val skip = active?.segment
        val autoSkip = if (active?.isOpening == true) current.autoSkipOpening else current.autoSkipEnding
        if (skip != null && autoSkip && autoSkipped.add(skip)) {
            player.seekTo((skip.stop!! * 1000).toLong())
        }
        val remaining = remainingSeconds(position, duration)
        if (current.sleepTimer == SleepTimer.AfterEpisode && remaining in 1..30 && !current.sleepWarning)
            update { it.copy(sleepWarning = true) }
        val next = current.release.episodeAfter(current.episode.id)
        if (current.sleepTimer != SleepTimer.AfterEpisode && remaining in 1..60 && next != null && queuedItem == null &&
            player.currentMediaItemIndex == player.mediaItemCount - 1 &&
            player.currentMediaItem?.mediaId == current.episode.id) {
            streamFor(next, current.quality)?.let { (_, url) ->
                val item = mediaItem(next, url)
                queuedItem = item
                val index = current.release.orderedEpisodes().indexOfFirst { it.id == next.id }
                preloadManager.setCurrentPlayingIndex(index - 1)
                preloadManager.add(item, index)
                preloadManager.invalidate()
            }
        }
        if (current.sleepTimer == SleepTimer.AfterEpisode && duration > 0 && remaining <= 0) {
            triggerSleep(); return
        }
        val countdown = nextCountdown(current.autoNext, current.sleepTimer, remaining, hasNext = next != null)
        update { it.copy(positionMs = position, durationMs = duration, skip = if (autoSkip) null else skip,
            skipOpening = active?.isOpening == true, nextCountdown = countdown) }
        if (current.autoNext && current.sleepTimer != SleepTimer.AfterEpisode && duration > 0 && remaining <= 0 && next != null && player.mediaItemCount == 1) playEpisode(next.id)
        if (position - lastSync >= 15_000 || position < lastSync) {
            lastSync = position
            viewModelScope.launch { saveProgress() }
        }
    }

    fun nextEpisode(keepPanel: Boolean = false) {
        val current = content ?: return
        current.release.episodeAfter(current.episode.id)?.let { playEpisode(it.id, keepPanel) }
    }

    fun pauseAndSave() { player.pause(); viewModelScope.launch { saveProgress() } }
    /** Back from the player: the position is stored locally right away; the server and Watch Next follow in the background. */
    fun close(onSaved: () -> Unit) = viewModelScope.launch {
        setSleepTimer(SleepTimer.Off)
        saveProgress()
        content?.let { current ->
            appScope.launch { watchNext.update(current.release, current.episode, store.progress(current.episode.id)) }
        }
        onSaved()
    }

    private suspend fun saveProgress() {
        val current = content ?: return
        val position = player.currentPosition.coerceAtLeast(0)
        val duration = player.duration.coerceAtLeast(0)
        saveProgress(current, position, duration)
    }

    private suspend fun saveProgress(current: PlayerContent, position: Long, duration: Long) {
        store.save(current.episode.id, position, duration, current.release.id)
        // The network part must neither delay leaving the player nor be cancelled with this screen.
        if (!tokenStore.get().isNullOrBlank()) appScope.launch {
            repository.saveTimecode(current.episode.id, position / 1000.0, isWatched(position, duration))
        }
    }

    private fun mediaItem(episode: Episode, url: String): MediaItem = MediaItem.Builder()
        .setMediaId(episode.id).setUri(url).build()

    override fun onCleared() {
        sleepDeadline = 0L
        seekJob?.cancel(); seekHideJob?.cancel(); retryJob?.cancel()
        android.util.Log.i("EpisodePlayer", "released")
        nightAudio.release(); player.release(); preloadManager.release(); super.onCleared()
    }

    companion object {
        val RETRY_DELAYS_MS = longArrayOf(2_000, 4_000, 8_000)
        /** Marker understood by the screen: show the localized network message with actions. */
        const val PLAYBACK_FAILED = "playback_failed"
        val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
    }
}
