package ru.feskolech.libriatv.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val mirrorKey = stringPreferencesKey("api_mirror")
    private val automaticCrashReportsKey = booleanPreferencesKey("automatic_crash_reports")
    val mirror = context.settingsDataStore.data.map { it[mirrorKey] ?: "anilibria.top" }
    suspend fun mirror(): String = mirror.first()
    suspend fun setMirror(host: String) {
        require(host == "anilibria.top" || host == "aniliberty.top")
        context.settingsDataStore.edit { it[mirrorKey] = host }
    }
    suspend fun automaticCrashReports(): Boolean = context.settingsDataStore.data.first()[automaticCrashReportsKey] ?: false
    suspend fun setAutomaticCrashReports(enabled: Boolean) {
        context.settingsDataStore.edit { it[automaticCrashReportsKey] = enabled }
    }
}
