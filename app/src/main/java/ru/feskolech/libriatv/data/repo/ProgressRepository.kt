package ru.feskolech.libriatv.data.repo

import javax.inject.Inject
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release

data class ServerTimecode(val episodeId: String, val positionMs: Long, val watched: Boolean)
data class ContinueItem(val release: Release, val episode: Episode, val progress: PlaybackProgress)

/** Server's watched flag is authoritative, except for explicit local manual marks. */
fun mergeProgress(local: PlaybackProgress?, server: ServerTimecode?): PlaybackProgress? {
    if (local == null && server == null) return null
    return PlaybackProgress(
        maxOf(local?.positionMs ?: 0, server?.positionMs ?: 0),
        local?.durationMs ?: 0,
        server?.watched,
        local?.manualWatched == true,
    )
}

@javax.inject.Singleton
class ProgressRepository @Inject constructor(
    private val repository: ApiRepository,
    private val store: PlaybackStore,
    private val tokens: TokenStore,
) {
    suspend fun markWatched(episode: Episode, releaseId: Int) {
        val seconds = (store.progress(episode.id)?.positionMs ?: 0) / 1000.0
        store.markWatched(episode.id, releaseId)
        if (!tokens.get().isNullOrBlank()) repository.saveTimecode(episode.id, seconds, true)
    }
    suspend fun serverTimecodes(): ApiResult<List<ServerTimecode>> {
        if (tokens.get().isNullOrBlank()) return ApiResult.Success(emptyList())
        return repository.timecodes()
    }

    suspend fun releaseProgress(release: Release): Map<String, PlaybackProgress> {
        val server = (serverTimecodes() as? ApiResult.Success)?.value.orEmpty().associateBy { it.episodeId }
        return release.episodes.mapNotNull { episode ->
            mergeProgress(store.progress(episode.id), server[episode.id])?.let { episode.id to it }
        }.toMap()
    }

    /** episode id -> release id; episodes never move between releases, so this is cached for the process. */
    private val releaseOfEpisode = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val requests = kotlinx.coroutines.sync.Semaphore(4)

    /**
     * Unfinished episodes, one per release. Lookups run in parallel (at most 4 requests at a time)
     * instead of one after another, which made a long watch history hold up the home screen.
     */
    suspend fun continueItems(): List<ContinueItem> = kotlinx.coroutines.coroutineScope {
        val local = store.entries().associateBy { it.episodeId }
        val server = (serverTimecodes() as? ApiResult.Success)?.value.orEmpty().associateBy { it.episodeId }
        val episodeIds = (local.keys + server.keys).toList()
        val releaseIds = episodeIds.map { id ->
            async {
                local[id]?.releaseId ?: releaseOfEpisode[id] ?: requests.withPermit {
                    (repository.episodeDetails(id) as? ApiResult.Success)?.value?.releaseId
                }?.also { releaseOfEpisode[id] = it }
            }
        }.mapNotNull { it.await() }.distinct()
        releaseIds.map { id ->
            async {
                val release = requests.withPermit { (repository.release(id.toString()) as? ApiResult.Success)?.value }
                    ?: return@async null
                release.episodes.mapNotNull { episode ->
                    mergeProgress(local[episode.id]?.progress, server[episode.id])?.let { progress ->
                        if (progress.positionMs > 0 && !progress.watched) ContinueItem(release, episode, progress) else null
                    }
                }.maxByOrNull { it.episode.ordinal ?: 0.0 }
            }
        }.mapNotNull { it.await() }
    }
}
