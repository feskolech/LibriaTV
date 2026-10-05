package ru.feskolech.libriatv.data.repo

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class TorrServeFile(val index: Int, val path: String, val size: Long, val episode: Int?) {
    val name: String get() = path.substringAfterLast('/')
}

data class TorrServeListing(val hash: String, val files: List<TorrServeFile>)

fun episodeFromFilename(path: String): Int? {
    val name = path.substringAfterLast('/').substringBeforeLast('.')
    val patterns = listOf(
        Regex("(?i)\\bS\\d{1,2}E(\\d{1,3})\\b"),
        Regex("(?i)\\bE(\\d{1,3})\\b"),
        Regex("(?iu)(?:серия|episode|ep)\\s*(\\d{1,3})"),
        Regex("(?:^|\\s)-\\s*(\\d{1,3})(?=\\s|\\[|$)"),
        Regex("\\[(\\d{1,3})]"),
        Regex("^(\\d{1,3})$"),
    )
    return patterns.firstNotNullOfOrNull { it.find(name)?.groupValues?.get(1)?.toIntOrNull() }
}

fun parseTorrServeFiles(body: String): List<TorrServeFile> {
    val root = Json.parseToJsonElement(body).jsonObject
    return (root["file_stats"] ?: root["files"])?.jsonArray.orEmpty().mapNotNull { item ->
        val row = item.jsonObject
        val path = row["path"]?.jsonPrimitive?.content ?: row["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        if (path.substringAfterLast('.', "").lowercase() !in setOf("mkv", "mp4", "avi", "mov", "webm", "m4v", "ts")) return@mapNotNull null
        val index = row["id"]?.jsonPrimitive?.intOrNull ?: row["index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val size = row["length"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: row["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
        TorrServeFile(index, path, size, episodeFromFilename(path))
    }.sortedWith(compareBy<TorrServeFile> { it.episode ?: Int.MAX_VALUE }.thenBy { it.path })
}

class TorrServeClient @Inject constructor() {
    // Local service only: no AniLibria authorization, fallback DNS, or API interceptors.
    // The probe must be fast (1 s), but adding a magnet on a slow TV box can take several seconds.
    private val client = OkHttpClient.Builder().connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()
    private val probe = client.newBuilder().readTimeout(1, TimeUnit.SECONDS).callTimeout(1, TimeUnit.SECONDS).build()
    private val base = "http://127.0.0.1:8090"

    suspend fun available(): Boolean = withContext(Dispatchers.IO) {
        runCatching { probe.newCall(Request.Builder().url("$base/echo").build()).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    suspend fun addAndList(magnet: String): TorrServeListing = withContext(Dispatchers.IO) {
        val added = post("""{"action":"add","link":"${magnet.replace("\\", "\\\\").replace("\"", "\\\"")}","save_to_db":false}""")
        val hash = Json.parseToJsonElement(added).jsonObject["hash"]?.jsonPrimitive?.content
            ?: Regex("(?i)btih:([a-z0-9]+)").find(magnet)?.groupValues?.get(1)
            ?: error("Missing torrent hash")
        val deadline = System.currentTimeMillis() + 15_000
        do {
            val listing = runCatching { parseTorrServeFiles(post("""{"action":"get","hash":"$hash"}""")) }.getOrDefault(emptyList())
            if (listing.isNotEmpty()) return@withContext TorrServeListing(hash, listing)
            delay(500)
        } while (System.currentTimeMillis() < deadline)
        error("Torrent files are unavailable")
    }

    fun streamUrl(hash: String, file: TorrServeFile): String =
        "$base/stream/${URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")}" +
            "?link=$hash&index=${file.index}&play"

    private fun post(json: String): String {
        val request = Request.Builder().url("$base/torrents")
            .post(json.toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("TorrServe HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }
}
