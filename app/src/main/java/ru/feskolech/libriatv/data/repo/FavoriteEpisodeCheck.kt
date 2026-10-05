package ru.feskolech.libriatv.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.feskolech.libriatv.domain.Release

data class NewFavoriteEpisode(val release: Release, val from: Int, val to: Int)

/** Favorites releases omit latest_episode on the live API, but include the episode list. */
fun Release.latestFavoriteOrdinal(): Int? =
    (latestEpisode?.ordinal ?: episodes.mapNotNull { it.ordinal }.maxOrNull())?.toInt()

/** Missing history is a baseline, and newly favorited releases do not announce old episodes. */
fun compareFavoriteEpisodes(previous: Map<Int, Int>?, current: List<Release>): List<NewFavoriteEpisode> {
    if (previous == null) return emptyList()
    return current.mapNotNull { release ->
        val old = previous[release.id] ?: return@mapNotNull null
        val now = release.latestFavoriteOrdinal() ?: return@mapNotNull null
        if (now > old) NewFavoriteEpisode(release, old + 1, now) else null
    }
}

@Singleton
class FavoriteEpisodeStore @Inject constructor(@ApplicationContext private val context: Context) {
    private fun key(userId: Int) = stringPreferencesKey("favorite_ordinals_$userId")

    suspend fun previous(userId: Int): Map<Int, Int>? = context.libriaDataStore.data.first()[key(userId)]
        ?.let { encoded -> runCatching { Json.decodeFromString<Map<Int, Int>>(encoded) }.getOrNull() }

    suspend fun save(userId: Int, releases: List<Release>) {
        val values = releases.mapNotNull { release ->
            release.latestFavoriteOrdinal()?.let { release.id to it }
        }.toMap()
        context.libriaDataStore.edit { it[key(userId)] = Json.encodeToString(values) }
    }

    // Called only from the debug receiver used to verify the notification on an emulator.
    suspend fun lowerFirstSavedOrdinal(userId: Int): Boolean {
        var changed = false
        context.libriaDataStore.edit { preferences ->
            val key = key(userId)
            val encoded = preferences[key]
            if (encoded != null) {
                val map = runCatching { Json.decodeFromString<Map<Int, Int>>(encoded) }.getOrNull().orEmpty()
                val first = map.entries.firstOrNull { it.value > 1 }
                if (first != null) {
                    preferences[key] = Json.encodeToString(map + (first.key to first.value - 1))
                    changed = true
                }
            }
        }
        return changed
    }
}
