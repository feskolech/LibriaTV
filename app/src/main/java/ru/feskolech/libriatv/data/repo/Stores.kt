package ru.feskolech.libriatv.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libriaDataStore by preferencesDataStore(name = "libria_settings")

@Singleton class TokenStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("token")
    val token = context.libriaDataStore.data.map { it[key] }
    suspend fun get(): String? = token.first()
    suspend fun set(value: String?) { context.libriaDataStore.edit { if (value == null) it.remove(key) else it[key] = value } }
}

@Singleton class DeviceIdStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("device_id")
    suspend fun get(): String {
        val existing = context.libriaDataStore.data.first()[key]
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        var result = generated
        context.libriaDataStore.edit { prefs ->
            result = prefs[key] ?: generated
            prefs[key] = result
        }
        return result
    }
}
