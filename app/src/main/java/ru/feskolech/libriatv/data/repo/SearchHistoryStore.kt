package ru.feskolech.libriatv.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchDataStore by preferencesDataStore(name = "search_history")

/** Last [LIMIT] search queries, newest first. */
@Singleton
class SearchHistoryStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("queries")

    val queries: Flow<List<String>> = context.searchDataStore.data.map { decode(it[key]) }

    suspend fun add(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        context.searchDataStore.edit { it[key] = encode(push(decode(it[key]), clean)) }
    }

    suspend fun clear() { context.searchDataStore.edit { it.remove(key) } }

    companion object {
        const val LIMIT = 10

        fun push(history: List<String>, query: String): List<String> =
            (listOf(query) + history.filterNot { it.equals(query, ignoreCase = true) }).take(LIMIT)

        private fun decode(raw: String?): List<String> = raw?.split('\n')?.filter(String::isNotBlank).orEmpty()
        private fun encode(list: List<String>): String = list.joinToString("\n")
    }
}
