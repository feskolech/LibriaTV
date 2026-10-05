package ru.feskolech.libriatv.data.repo

import javax.inject.Inject
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release

data class ServerTimecode(val episodeId: String, val positionMs: Long, val watched: Boolean)
data class ContinueItem(val release: Release, val episode: Episode, val progress: PlaybackProgress)

/** Server's watched flag is authoritative; otherwise retain the furthest position. */
fun mergeProgress(local: PlaybackProgress?, server: ServerTimecode?): PlaybackProgress? {
    if (local == null && server == null) return null
    return PlaybackProgress(
        maxOf(local?.positionMs ?: 0, server?.positionMs ?: 0),
        local?.durationMs ?: 0,
        server?.watched,
    )
}

class ProgressRepository @Inject constructor(
    private val repository: ApiRepository,
    private val store: PlaybackStore,
    private val tokens: TokenStore,
) {
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

    suspend fun continueItems(): List<ContinueItem> {
        val local = store.entries().associateBy { it.episodeId }
        val server = (serverTimecodes() as? ApiResult.Success)?.value.orEmpty().associateBy { it.episodeId }
        val episodeIds = local.keys + server.keys
        val releaseIds = episodeIds.mapNotNull { id ->
            local[id]?.releaseId ?: (repository.episodeDetails(id) as? ApiResult.Success)?.value?.releaseId
        }.distinct()
        return releaseIds.mapNotNull { id ->
            val release = (repository.release(id.toString()) as? ApiResult.Success)?.value ?: return@mapNotNull null
            release.episodes.mapNotNull { episode ->
                mergeProgress(local[episode.id]?.progress, server[episode.id])?.let { progress ->
                    if (progress.positionMs > 0 && !progress.watched) ContinueItem(release, episode, progress) else null
                }
            }.maxByOrNull { it.episode.ordinal ?: 0.0 }
        }
    }
}
