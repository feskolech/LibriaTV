package ru.feskolech.libriatv.ui.player

import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.Skip

/*
 * The player's decisions that do not need a player: episode order, stream choice, skip segments,
 * seek steps, the sleep deadline and the "next episode" countdown. Kept pure so they are unit-tested.
 */

/** Episodes in watching order (by number; episodes without one first, as the site lists them). */
internal fun Release.orderedEpisodes(): List<Episode> = episodes.sortedBy { it.ordinal ?: 0.0 }

internal fun Release.episodeAfter(id: String): Episode? =
    orderedEpisodes().let { list -> list.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { list.getOrNull(it + 1) } }

internal fun Release.episodeBefore(id: String): Episode? =
    orderedEpisodes().let { list -> list.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { list.getOrNull(it - 1) } }

/** The release has nothing after this episode: when it ends, the player goes back to the release page. */
internal fun Release.isLastEpisode(id: String): Boolean = episodeAfter(id) == null

/** Number keys on the remote: episode N by its number, not by its position in the list. */
internal fun Release.episodeNumber(number: Int): Episode? = episodes.firstOrNull { it.ordinal?.toInt() == number }

/** The preferred quality if the episode has it, otherwise the best one it has; null without any stream. */
internal fun streamFor(episode: Episode, preferredQuality: Int): Pair<Int, String>? =
    listOf(1080 to episode.hls1080, 720 to episode.hls720, 480 to episode.hls480)
        .mapNotNull { (quality, url) -> url?.takeIf { it.isNotBlank() }?.let { quality to it } }
        .let { options -> options.firstOrNull { it.first == preferredQuality } ?: options.firstOrNull() }

/** One step down for the "buffers a lot, try lower quality?" hint, if the episode has that stream. */
internal fun lowerQuality(episode: Episode, quality: Int): Int? = when (quality) {
    1080 -> if (!episode.hls720.isNullOrBlank()) 720 else null
    720 -> if (!episode.hls480.isNullOrBlank()) 480 else null
    else -> null
}

/** The opening or ending the playhead is inside, if any. */
internal data class ActiveSkip(val segment: Skip, val isOpening: Boolean)

internal fun activeSkip(episode: Episode, positionMs: Long): ActiveSkip? {
    val seconds = positionMs / 1000.0
    fun Skip?.covers() = this != null && start != null && stop != null && seconds >= start && seconds < stop
    return when {
        episode.opening.covers() -> ActiveSkip(episode.opening!!, isOpening = true)
        episode.ending.covers() -> ActiveSkip(episode.ending!!, isOpening = false)
        else -> null
    }
}

/** Holding Left/Right on the progress bar speeds up: 10 s, then 30 s, then 60 s per step. */
internal fun seekStepSeconds(repeat: Int): Int = when { repeat >= 8 -> 60; repeat >= 3 -> 30; else -> 10 }

/** When a timed sleep timer fires (elapsed-realtime ms), or 0 for no deadline. */
internal fun sleepDeadline(timer: SleepTimer, nowMs: Long): Long = when (timer) {
    SleepTimer.Minutes15 -> nowMs + 15 * 60_000L
    SleepTimer.Minutes30 -> nowMs + 30 * 60_000L
    SleepTimer.Minutes60 -> nowMs + 60 * 60_000L
    SleepTimer.Off, SleepTimer.AfterEpisode -> 0L
}

/** Whole seconds left in the episode, or [Long.MAX_VALUE] while the duration is unknown. */
internal fun remainingSeconds(positionMs: Long, durationMs: Long): Long =
    if (durationMs > 0) (durationMs - positionMs) / 1000 else Long.MAX_VALUE

/** Seconds shown on "Next episode in N" during the last 8 s, when the next one will start by itself. */
internal fun nextCountdown(autoNext: Boolean, sleepTimer: SleepTimer, remaining: Long, hasNext: Boolean): Int? =
    if (autoNext && sleepTimer != SleepTimer.AfterEpisode && remaining in 1..8 && hasNext) remaining.toInt() else null

/** The site counts an episode as watched from 90 %. */
internal fun isWatched(positionMs: Long, durationMs: Long): Boolean = durationMs > 0 && positionMs >= durationMs * 0.9
