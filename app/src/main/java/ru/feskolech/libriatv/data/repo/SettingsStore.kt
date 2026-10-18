package ru.feskolech.libriatv.data.repo

import android.content.Context
import android.app.ActivityManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val themeKey = stringPreferencesKey("appearance_theme")
    private val accentKey = intPreferencesKey("appearance_accent")
    private val scaleKey = intPreferencesKey("appearance_scale")
    val appearance = context.settingsDataStore.data.map {
        AppearanceSettings(it[themeKey] == "oled", (it[accentKey] ?: 0).coerceIn(0, 5),
            (it[scaleKey] ?: 100).takeIf { scale -> scale in listOf(90, 100, 115, 130) } ?: 100)
    }
    suspend fun setOled(value: Boolean) { context.settingsDataStore.edit { it[themeKey] = if (value) "oled" else "dark" } }
    suspend fun setAccent(value: Int) { require(value in 0..5); context.settingsDataStore.edit { it[accentKey] = value } }
    suspend fun setScale(value: Int) { require(value in listOf(90, 100, 115, 130)); context.settingsDataStore.edit { it[scaleKey] = value } }
    private val mirrorKey = stringPreferencesKey("api_mirror")
    private val automaticCrashReportsKey = booleanPreferencesKey("automatic_crash_reports")
    private val phoneRemoteKey = booleanPreferencesKey("phone_remote_enabled")
    private val homeVideoPreviewKey = booleanPreferencesKey("home_video_preview")
    private val previewDefault: Boolean get() {
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        return memory.totalMem >= 2L * 1024 * 1024 * 1024
    }
    suspend fun homeVideoPreview(): Boolean = context.settingsDataStore.data.first()[homeVideoPreviewKey] ?: previewDefault
    suspend fun setHomeVideoPreview(enabled: Boolean) {
        context.settingsDataStore.edit { it[homeVideoPreviewKey] = enabled }
    }
    val mirror = context.settingsDataStore.data.map { it[mirrorKey] ?: "aniliberty.top" }
    suspend fun mirror(): String = mirror.first()
    suspend fun setMirror(host: String) {
        require(host == "anilibria.top" || host == "aniliberty.top")
        context.settingsDataStore.edit { it[mirrorKey] = host }
    }
    suspend fun automaticCrashReports(): Boolean = context.settingsDataStore.data.first()[automaticCrashReportsKey] ?: false
    suspend fun setAutomaticCrashReports(enabled: Boolean) {
        context.settingsDataStore.edit { it[automaticCrashReportsKey] = enabled }
    }
    suspend fun phoneRemoteEnabled(): Boolean = context.settingsDataStore.data.first()[phoneRemoteKey] ?: false
    suspend fun setPhoneRemoteEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[phoneRemoteKey] = enabled }
    }
}

data class AppearanceSettings(val oled: Boolean = false, val accent: Int = 0, val scale: Int = 100)
