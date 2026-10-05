package ru.feskolech.libriatv.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/** Fetches only one 480p transport segment at a time; never uses the playback loader. */
internal class SeekFrameLoader(private val context: Context, private val client: OkHttpClient) {
    private val mutex = Mutex()
    private val frames = object : LruCache<String, Bitmap>(30) {}

    suspend fun frame(episodeId: String, playlistUrl: String, positionMs: Long): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val key = "$episodeId:${positionMs / 10_000}"
            frames.get(key)?.let { return@withContext it }
            runCatching {
                val playlist = loadPlaylist(playlistUrl) ?: return@runCatching null
                val (segmentUrl, offsetMs) = segmentAt(playlist.first, playlist.second, positionMs)
                    ?: return@runCatching null
                coroutineContext.ensureActive()
                val bytes = fetch(segmentUrl) ?: return@runCatching null
                val file = File.createTempFile("seek-frame-", ".ts", context.cacheDir)
                try {
                    file.writeBytes(bytes)
                    coroutineContext.ensureActive()
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val bitmap = retriever.getFrameAtTime(offsetMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: return@runCatching null
                        val width = 320
                        val scaled = Bitmap.createScaledBitmap(bitmap, width,
                            (bitmap.height * width.toFloat() / bitmap.width).toInt().coerceAtLeast(1), true)
                        if (scaled !== bitmap) bitmap.recycle()
                        coroutineContext.ensureActive()
                        frames.put(key, scaled)
                        scaled
                    } finally { retriever.release() }
                } finally { file.delete() }
            }.getOrNull()
        }
    }

    private suspend fun loadPlaylist(url: String): Pair<String, String>? {
        var address = url
        repeat(2) {
            val body = fetch(address)?.toString(Charsets.UTF_8) ?: return null
            if (body.contains("#EXTINF:")) return address to body
            val lines = body.lines()
            val variant = lines.firstOrNull { it.trim().startsWith("#EXT-X-STREAM-INF") }
                ?.let { line -> lines.drop(lines.indexOf(line) + 1).firstOrNull { !it.startsWith('#') && it.isNotBlank() } }
                ?: return null
            address = address.toHttpUrlOrNull()?.resolve(variant.trim())?.toString() ?: return null
        }
        return null
    }

    private fun segmentAt(url: String, body: String, positionMs: Long): Pair<String, Long>? {
        var elapsed = 0L
        val lines = body.lines()
        for ((index, line) in lines.withIndex()) {
            if (!line.startsWith("#EXTINF:")) continue
            val length = (line.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: continue)
                .times(1000).toLong()
            val segment = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith('#') } ?: continue
            if (positionMs < elapsed + length) {
                val resolved = url.toHttpUrlOrNull()?.resolve(segment.trim())?.toString() ?: return null
                return resolved to (positionMs - elapsed).coerceAtLeast(0)
            }
            elapsed += length
        }
        return null
    }

    private suspend fun fetch(url: String): ByteArray? = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val bytes = runCatching {
                    response.use { if (it.isSuccessful) it.body?.bytes() else null }
                }.getOrNull()
                if (continuation.isActive) continuation.resume(bytes)
            }
        })
    }
}
