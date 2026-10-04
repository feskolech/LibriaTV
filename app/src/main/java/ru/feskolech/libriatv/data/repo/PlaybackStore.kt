package ru.feskolech.libriatv.data.repo

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.playbackDataStore by preferencesDataStore(name = "playback")

data class PlaybackProgress(val positionMs: Long, val durationMs: Long) {
    val watched: Boolean get() = durationMs > 0 && positionMs >= durationMs * 0.9
}

@Singleton
class PlaybackStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val qualityKey = intPreferencesKey("quality")
    private val autoSkipKey = androidx.datastore.preferences.core.booleanPreferencesKey("auto_skip")

    suspend fun quality(): Int = context.playbackDataStore.data.first()[qualityKey] ?: 1080
    suspend fun setQuality(value: Int) { context.playbackDataStore.edit { it[qualityKey] = value } }
    suspend fun autoSkip(): Boolean = context.playbackDataStore.data.first()[autoSkipKey] ?: false
    suspend fun setAutoSkip(value: Boolean) { context.playbackDataStore.edit { it[autoSkipKey] = value } }

    suspend fun progress(episodeId: String): PlaybackProgress? {
        val prefs = context.playbackDataStore.data.first()
        val position = prefs[longPreferencesKey("position_$episodeId")] ?: return null
        return PlaybackProgress(position, prefs[longPreferencesKey("duration_$episodeId")] ?: 0)
    }

    suspend fun save(episodeId: String, positionMs: Long, durationMs: Long) {
        context.playbackDataStore.edit {
            it[longPreferencesKey("position_$episodeId")] = positionMs.coerceAtLeast(0)
            it[longPreferencesKey("duration_$episodeId")] = durationMs.coerceAtLeast(0)
        }
    }
}
