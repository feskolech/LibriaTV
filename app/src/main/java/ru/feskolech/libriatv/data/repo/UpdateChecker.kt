package ru.feskolech.libriatv.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.feskolech.libriatv.BuildConfig

private val Context.updateDataStore by preferencesDataStore(name = "updates")
private val lastCheckKey = longPreferencesKey("last_check")
private val latestNotesKey = stringPreferencesKey("latest_notes")
private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

data class UpdateRelease(val version: String, val notes: String, val apkUrl: String)

sealed interface UpdateCheckResult {
    data class Available(val release: UpdateRelease) : UpdateCheckResult
    data object Current : UpdateCheckResult
    data object NoRelease : UpdateCheckResult
    data object Unavailable : UpdateCheckResult
    data object Skipped : UpdateCheckResult
}

internal fun compareVersions(left: String, right: String): Int {
    fun parts(version: String): Pair<List<Int>, List<String>?>? {
        val clean = version.removePrefix("v")
        val match = Regex("([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?")
            .matchEntire(clean) ?: return null
        val numbers = (1..3).map { match.groupValues[it].toIntOrNull() ?: return null }
        return numbers to match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.')
    }
    val a = parts(left) ?: return 0
    val b = parts(right) ?: return 0
    for (i in 0..2) {
        val difference = a.first[i].compareTo(b.first[i])
        if (difference != 0) return difference
    }
    val aPre = a.second ?: return if (b.second == null) 0 else 1
    val bPre = b.second ?: return -1
    for (i in 0 until maxOf(aPre.size, bPre.size)) {
        val leftPart = aPre.getOrNull(i) ?: return -1
        val rightPart = bPre.getOrNull(i) ?: return 1
        val leftNumber = leftPart.toIntOrNull()
        val rightNumber = rightPart.toIntOrNull()
        val comparison = when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> leftPart.compareTo(rightPart)
        }
        if (comparison != 0) return comparison
    }
    return 0
}

internal fun parseUpdateRelease(json: String): UpdateRelease? = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    val tag = root["tag_name"]?.jsonPrimitive?.content ?: return null
    val asset = root["assets"]?.jsonArray?.firstOrNull { item ->
        item.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true
    }?.jsonObject ?: return null
    val url = asset["browser_download_url"]?.jsonPrimitive?.content ?: return null
    if (!url.startsWith("https://")) return null
    UpdateRelease(tag.removePrefix("v"), root["body"]?.jsonPrimitive?.content.orEmpty(), url)
}.getOrNull()

internal sealed interface GithubReleaseResponse {
    data class Release(val value: UpdateRelease) : GithubReleaseResponse
    data object NotFound : GithubReleaseResponse
    data object Failed : GithubReleaseResponse
}

internal fun fetchLatestRelease(client: OkHttpClient, url: String): GithubReleaseResponse {
    val request = Request.Builder().url(url)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "LibriaTV/${BuildConfig.VERSION_NAME}")
        .build()
    client.newCall(request).execute().use { response ->
        if (response.code == 404) return GithubReleaseResponse.NotFound
        if (!response.isSuccessful) return GithubReleaseResponse.Failed
        val release = parseUpdateRelease(response.body?.string().orEmpty()) ?: return GithubReleaseResponse.Failed
        return GithubReleaseResponse.Release(release)
    }
}

@Singleton
class UpdateChecker @Inject constructor(@ApplicationContext private val context: Context) {
    val latestNotes = context.updateDataStore.data.map { it[latestNotesKey].orEmpty() }
    // Keep GitHub requests separate from the AniLibria client: no account token may reach GitHub.
    private val client = OkHttpClient()

    suspend fun check(force: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val last = context.updateDataStore.data.first()[lastCheckKey] ?: 0L
        if (!force && now - last in 0 until CHECK_INTERVAL_MS) return@withContext UpdateCheckResult.Skipped
        try {
            val response = fetchLatestRelease(client,
                "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            context.updateDataStore.edit {
                it[lastCheckKey] = now
                if (response is GithubReleaseResponse.Release) it[latestNotesKey] = response.value.notes
            }
            when (response) {
                is GithubReleaseResponse.Release -> if (compareVersions(response.value.version, BuildConfig.VERSION_NAME) > 0)
                    UpdateCheckResult.Available(response.value) else UpdateCheckResult.Current
                GithubReleaseResponse.NotFound -> UpdateCheckResult.NoRelease
                GithubReleaseResponse.Failed -> UpdateCheckResult.Unavailable
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            UpdateCheckResult.Unavailable
        }
    }

    suspend fun download(release: UpdateRelease, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "LibriaTV-${release.version}.apk")
        val partial = File(directory, "LibriaTV-${release.version}.part")
        try {
            client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty download")
                val total = body.contentLength()
                var received = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            if (total > 0) onProgress((received * 100 / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            if (!partial.renameTo(target)) throw IOException("Could not save APK")
            target
        } finally {
            partial.delete()
        }
    }
}
