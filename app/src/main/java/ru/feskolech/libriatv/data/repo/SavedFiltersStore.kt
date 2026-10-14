package ru.feskolech.libriatv.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.feskolech.libriatv.domain.CatalogFilter

private val Context.catalogDataStore by preferencesDataStore(name = "catalog")

/** A catalog filter the viewer kept as a chip of its own, with the name shown on it. */
data class SavedCatalogFilter(val name: String, val filter: CatalogFilter, val titleSorted: Boolean)

@Serializable
private data class SavedFilterJson(
    val name: String,
    val genres: List<String> = emptyList(), val types: List<String> = emptyList(),
    val seasons: List<String> = emptyList(), val statuses: List<String> = emptyList(),
    val fromYear: Int? = null, val toYear: Int? = null, val sorting: String? = null,
    val titleSorted: Boolean = false,
)

/** The viewer's saved catalog filters, newest last. Kept on the device only. */
@Singleton
class SavedFiltersStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("saved_filters")
    private val json = Json { ignoreUnknownKeys = true }

    val filters: Flow<List<SavedCatalogFilter>> = context.catalogDataStore.data.map { prefs ->
        decode(prefs[key])
    }

    suspend fun add(saved: SavedCatalogFilter) = update { it.filterNot { old -> old.sameAs(saved) } + saved }
    suspend fun remove(saved: SavedCatalogFilter) = update { it.filterNot { old -> old.sameAs(saved) } }

    private suspend fun update(change: (List<SavedCatalogFilter>) -> List<SavedCatalogFilter>) {
        context.catalogDataStore.edit { prefs -> prefs[key] = encode(change(decode(prefs[key])).takeLast(MAX)) }
    }

    private fun SavedCatalogFilter.sameAs(other: SavedCatalogFilter) = filter == other.filter && titleSorted == other.titleSorted

    private fun decode(raw: String?): List<SavedCatalogFilter> = runCatching {
        json.decodeFromString<List<SavedFilterJson>>(raw ?: return emptyList()).map {
            SavedCatalogFilter(it.name, CatalogFilter(it.genres.toSet(), it.types.toSet(), it.seasons.toSet(),
                it.statuses.toSet(), it.fromYear, it.toYear, it.sorting), it.titleSorted)
        }
    }.getOrDefault(emptyList())

    private fun encode(list: List<SavedCatalogFilter>): String = json.encodeToString(list.map {
        SavedFilterJson(it.name, it.filter.genres.toList(), it.filter.types.toList(), it.filter.seasons.toList(),
            it.filter.statuses.toList(), it.filter.fromYear, it.filter.toYear, it.filter.sorting, it.titleSorted)
    })

    private companion object { const val MAX = 8 }
}
