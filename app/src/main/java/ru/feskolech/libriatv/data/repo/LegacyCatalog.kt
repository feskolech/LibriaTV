package ru.feskolech.libriatv.data.repo

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.feskolech.libriatv.BuildConfig
import ru.feskolech.libriatv.domain.Episode
import ru.feskolech.libriatv.domain.Release
import ru.feskolech.libriatv.domain.Skip

/**
 * Read-only fallback to the legacy AniLibria API (the one the old TV app uses). The v1 API hides
 * some licensed titles from search and release pages depending on the viewer's country (e.g. Black
 * Clover from Belarus), while the legacy API still lists them and serves the same video. Release and
 * episode ids are shared between both APIs, so progress sync keeps working.
 *
 * It gets its own client: no account token and none of the v1 interceptors ever reach this host.
 */
@Singleton
class LegacyCatalog(private val url: String) {
    @Inject constructor() : this(DEFAULT_URL)

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Releases found by the legacy search, or an empty list when the service is unreachable. */
    suspend fun search(query: String): List<Release> =
        call("query" to "search", "search" to query, "filter" to SEARCH_FIELDS)
            ?.let { it as? JsonArray }.orEmpty().mapNotNull { (it as? JsonObject)?.toRelease(withEpisodes = false) }

    /** Full release with its episodes, or null when the legacy API does not have it either. */
    suspend fun release(id: Int): Release? =
        (call("query" to "release", "id" to id.toString()) as? JsonObject)?.toRelease(withEpisodes = true)

    private suspend fun call(vararg params: Pair<String, String>): JsonElement? = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder().url(url).post(body)
                .header("mobileApp", "true")
                .header("App-Id", "ru.radiationx.anilibria.app.tv")
                .header("App-Ver-Name", "1.3.2")
                .header("App-Ver-Code", "8")
                .header("User-Agent", "mobileApp Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.170 Safari/537.36 OPR/53.0.2907.68")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val text = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(text).jsonObject
                if (root["status"]?.jsonPrimitive?.contentOrNull != "true") null else root["data"]
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val DEFAULT_URL = "https://wwnd.space/public/api/index.php"
        const val SEARCH_FIELDS = "id,code,names,poster,year,season,type,genres,description,status,statusCode,last,series,blockedInfo"
    }
}

internal fun JsonObject.toRelease(withEpisodes: Boolean): Release? {
    val id = str("id")?.toIntOrNull() ?: return null
    val names = (this["names"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    val episodes = if (!withEpisodes) emptyList() else
        (this["playlist"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toEpisode() }
            .sortedBy { it.ordinal ?: 0.0 }
    val type = str("type")?.substringBefore(',')?.trim()
    return Release(
        id = id, title = names.firstOrNull().orEmpty(), alias = str("code"), description = str("description"),
        posterUrl = absoluteImageUrl(str("poster")),
        freshAt = str("last")?.toLongOrNull()?.let { java.time.Instant.ofEpochSecond(it).toString() },
        genres = (this["genres"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
        episodes = episodes, latestEpisode = episodes.lastOrNull(),
        year = str("year")?.toIntOrNull(), type = type,
        season = str("season")?.replaceFirstChar { it.uppercase() },
        isOngoing = str("statusCode")?.let { it == "1" },
        regionBlocked = (this["blockedInfo"] as? JsonObject)?.get("blocked")?.jsonPrimitive?.contentOrNull == "true" &&
            episodes.isEmpty(),
    )
}

private fun JsonObject.toEpisode(): Episode? {
    val id = str("uuid") ?: return null
    val skips = this["skips"] as? JsonObject
    fun skip(name: String): Skip? = (skips?.get(name) as? JsonArray)?.takeIf { it.size == 2 }?.let {
        Skip(it[0].jsonPrimitive.doubleOrNull, it[1].jsonPrimitive.doubleOrNull)
    }
    return Episode(
        id = id, name = str("name").orEmpty(),
        ordinal = this["ordinal"]?.jsonPrimitive?.doubleOrNull ?: str("id")?.toDoubleOrNull(),
        opening = skip("opening"), ending = skip("ending"),
        previewUrl = absoluteImageUrl(str("poster")),
        hls480 = str("sd"), hls720 = str("hd"), hls1080 = str("fullhd"),
    )
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
