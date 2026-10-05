package ru.feskolech.libriatv.data.repo

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.playbackDataStore by preferencesDataStore(name = "playback")

data class PlaybackProgress(val positionMs: Long, val durationMs: Long, val serverWatched: Boolean? = null,
    val manualWatched: Boolean = false) {
    val watched: Boolean get() = manualWatched || (serverWatched ?: (durationMs > 0 && positionMs >= durationMs * 0.9))
}

data class PlaybackEntry(val episodeId: String, val releaseId: Int?, val progress: PlaybackProgress)

@Singleton
class PlaybackStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val qualityKey = intPreferencesKey("quality")
    private val autoSkipKey = androidx.datastore.preferences.core.booleanPreferencesKey("auto_skip")
    private val autoNextKey = androidx.datastore.preferences.core.booleanPreferencesKey("auto_next")

    suspend fun quality(): Int = context.playbackDataStore.data.first()[qualityKey] ?: 1080
    suspend fun setQuality(value: Int) { context.playbackDataStore.edit { it[qualityKey] = value } }
    private val autoSkipEndingKey = androidx.datastore.preferences.core.booleanPreferencesKey("auto_skip_ending")

    /** Opening auto-skip keeps the original "auto_skip" key so earlier choices survive the split. */
    suspend fun autoSkipOpening(): Boolean = context.playbackDataStore.data.first()[autoSkipKey] ?: false
    suspend fun setAutoSkipOpening(value: Boolean) { context.playbackDataStore.edit { it[autoSkipKey] = value } }
    suspend fun autoSkipEnding(): Boolean = context.playbackDataStore.data.first()[autoSkipEndingKey] ?: false
    suspend fun setAutoSkipEnding(value: Boolean) { context.playbackDataStore.edit { it[autoSkipEndingKey] = value } }
    suspend fun autoNext(): Boolean = context.playbackDataStore.data.first()[autoNextKey] ?: true
    suspend fun setAutoNext(value: Boolean) { context.playbackDataStore.edit { it[autoNextKey] = value } }

    private val speedKey = androidx.datastore.preferences.core.floatPreferencesKey("speed")
    private val frameRateMatchKey = androidx.datastore.preferences.core.booleanPreferencesKey("frame_rate_match")
    private val nightModeKey = androidx.datastore.preferences.core.booleanPreferencesKey("night_mode")

    suspend fun speed(): Float = context.playbackDataStore.data.first()[speedKey] ?: 1f
    suspend fun setSpeed(value: Float) { context.playbackDataStore.edit { it[speedKey] = value } }
    /** Switch the TV refresh rate to the video frame rate (24p anime on a 60 Hz panel judders). On by default. */
    suspend fun frameRateMatch(): Boolean = context.playbackDataStore.data.first()[frameRateMatchKey] ?: true
    suspend fun setFrameRateMatch(value: Boolean) { context.playbackDataStore.edit { it[frameRateMatchKey] = value } }
    /** Compress loud peaks and lift quiet dialogue. Off by default. */
    suspend fun nightMode(): Boolean = context.playbackDataStore.data.first()[nightModeKey] ?: false
    suspend fun setNightMode(value: Boolean) { context.playbackDataStore.edit { it[nightModeKey] = value } }

    suspend fun progress(episodeId: String): PlaybackProgress? {
        val prefs = context.playbackDataStore.data.first()
        val position = prefs[longPreferencesKey("position_$episodeId")] ?: return null
        return PlaybackProgress(position, prefs[longPreferencesKey("duration_$episodeId")] ?: 0,
            manualWatched = prefs[intPreferencesKey("manual_watched_$episodeId")] == 1)
    }

    suspend fun entries(): List<PlaybackEntry> {
        val prefs = context.playbackDataStore.data.first()
        val indexed = prefs[stringPreferencesKey("episode_ids")].orEmpty().split(',').filter { it.isNotBlank() }
        val legacy = prefs.asMap().keys.mapNotNull { key -> key.name.removePrefix("position_").takeIf { key.name.startsWith("position_") } }
        return (indexed + legacy).distinct().mapNotNull { id ->
            val releaseId = prefs[intPreferencesKey("release_$id")]
            val position = prefs[longPreferencesKey("position_$id")] ?: return@mapNotNull null
            PlaybackEntry(id, releaseId, PlaybackProgress(position, prefs[longPreferencesKey("duration_$id")] ?: 0,
                manualWatched = prefs[intPreferencesKey("manual_watched_$id")] == 1))
        }
    }

    suspend fun save(episodeId: String, positionMs: Long, durationMs: Long, releaseId: Int? = null) {
        context.playbackDataStore.edit {
            it[longPreferencesKey("position_$episodeId")] = positionMs.coerceAtLeast(0)
            it[longPreferencesKey("duration_$episodeId")] = durationMs.coerceAtLeast(0)
            if (releaseId != null) {
                it[intPreferencesKey("release_$episodeId")] = releaseId
                val key = stringPreferencesKey("episode_ids")
                val ids = it[key].orEmpty().split(',').filter { value -> value.isNotBlank() }.toMutableSet()
                ids.add(episodeId)
                it[key] = ids.joinToString(",")
            }
        }
    }

    suspend fun markWatched(episodeId: String, releaseId: Int) {
        val previous = progress(episodeId)
        save(episodeId, previous?.positionMs ?: 0, previous?.durationMs ?: 0, releaseId)
        context.playbackDataStore.edit { it[intPreferencesKey("manual_watched_$episodeId")] = 1 }
    }

    suspend fun torrentPrompted(id: Int): Boolean =
        context.playbackDataStore.data.first()[intPreferencesKey("torrent_prompt_$id")] == 1

    suspend fun setTorrentPrompted(id: Int) {
        context.playbackDataStore.edit { it[intPreferencesKey("torrent_prompt_$id")] = 1 }
    }
}
