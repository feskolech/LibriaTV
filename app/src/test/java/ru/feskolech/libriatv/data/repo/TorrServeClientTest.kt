package ru.feskolech.libriatv.data.repo

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrServeClientTest {
    private val fixture = checkNotNull(javaClass.getResource("/torrserve-files.json")).readText()

    @Test fun parsesVideoFilesAndOrdersByEpisode() {
        val files = parseTorrServeFiles(fixture)
        assertEquals(listOf(1, 2, 3, 5, 5), files.map { it.episode })
        assertEquals(listOf(7, 4, 2, 9, 6), files.map { it.index })
        assertEquals(1073741824L, files.last().size)
        assertEquals(5, episodeFromFilename("[AniLibria] Title - 05 [1080p].mkv"))
        assertEquals(5, episodeFromFilename("S01E05.mkv"))
        assertEquals(5, episodeFromFilename("05.mkv"))
        assertEquals(1, episodeFromFilename("E01.mp4"))
        assertEquals(null, episodeFromFilename("Trailer.mkv"))
    }

    @Test fun echoUsesLocalServer() = runBlocking {
        MockWebServer().use { server ->
            server.start(8090)
            server.enqueue(MockResponse().setBody("ok"))
            assertTrue(TorrServeClient().available())
            assertEquals("/echo", server.takeRequest().path)
        }
    }

    @Test fun addAndGetUseExpectedPayloads() = runBlocking {
        MockWebServer().use { server ->
            server.start(8090)
            server.enqueue(MockResponse().setBody("{\"hash\":\"abc123\"}"))
            server.enqueue(MockResponse().setBody(fixture))
            val result = TorrServeClient().addAndList("magnet:?xt=urn:btih:abc123")
            assertEquals("abc123", result.hash)
            assertEquals(5, result.files.size)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"save_to_db\":false"))
            assertTrue(server.takeRequest().body.readUtf8().contains("\"action\":\"get\""))
            assertTrue(TorrServeClient().streamUrl(result.hash, result.files[0]).contains("index=7&play"))
        }
    }
}
