package ru.feskolech.libriatv.data.repo

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import ru.feskolech.libriatv.data.api.*

class ApiRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AniLibriaApi
    private lateinit var repo: ApiRepository

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/api/v1/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = false }
                .asConverterFactory("application/json".toMediaType()))
            .build().create(AniLibriaApi::class.java)
        repo = ApiRepository(api)
    }

    @After fun tearDown() { server.shutdown() }

    private fun fixture(name: String): String = javaClass.classLoader!!
        .getResourceAsStream("api/$name.json")!!.bufferedReader().use { it.readText() }
    private fun enqueue(name: String) { server.enqueue(MockResponse().setBody(fixture(name)).setHeader("Content-Type", "application/json")) }

    @Test fun scheduleNowMapsRealResponse() = runBlocking {
        enqueue("schedule-now")
        val result = repo.scheduleNow() as ApiResult.Success
        assertTrue(result.value.isNotEmpty())
        assertTrue(result.value.first().release.id > 0)
        assertTrue(result.value.first().release.posterUrl!!.startsWith("https://anilibria.top/"))
        assertEquals("/api/v1/anime/schedule/now", server.takeRequest().path)
    }

    @Test fun currentScheduleIncludesTomorrowAndReleaseMetadata() = runBlocking {
        enqueue("schedule-now")
        val result = repo.currentSchedule() as ApiResult.Success
        assertEquals(3, result.value.today.size)
        assertEquals(15, result.value.tomorrow.size)
        val release = result.value.tomorrow.first().release
        assertNotNull(release.year)
        assertNotNull(release.type)
        assertNotNull(release.season)
        assertNotNull(release.publishDay)
        assertNotNull(release.isOngoing)
        assertNotNull(release.episodesTotal)
    }

    @Test fun releaseMapsEpisodesAndStreams() = runBlocking {
        enqueue("release")
        val result = repo.release("10306") as ApiResult.Success
        assertEquals(10306, result.value.id)
        val episode = result.value.episodes.first()
        assertNotNull(episode.opening)
        assertNotNull(episode.ending)
        assertTrue(episode.hls480!!.startsWith("https://"))
        assertTrue(episode.hls720!!.startsWith("https://"))
        assertTrue(episode.hls1080!!.startsWith("https://"))
    }

    @Test fun torrentsMapRealResponse() = runBlocking {
        enqueue("torrents")
        val result = repo.torrents(10306) as ApiResult.Success
        assertTrue(result.value.first().magnet!!.startsWith("magnet:?"))
        assertTrue(result.value.first().size!! > 0)
    }

    @Test fun otpAndLoginParseSchemaFixtures() = runBlocking {
        enqueue("otp-get")
        val otp = api.otpGet(OtpGetRequestDto("device"))
        assertEquals("058701", otp.otp?.code)
        assertEquals(299.476915, otp.remainingTime!!, 0.001)
        assertTrue(server.takeRequest().body.readUtf8().contains("device_id"))
        enqueue("auth-login")
        assertEquals("example-test-token", api.login(LoginRequestDto("test", "password")).token)
        assertEquals("/api/v1/accounts/users/auth/login", server.takeRequest().path)
    }

    @Test fun httpErrorsRetainStatus() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        assertEquals(401, (repo.release("x") as ApiResult.Failure).status)
        server.enqueue(MockResponse().setResponseCode(422).setBody(fixture("error-422")))
        val validationError = repo.search("x") as ApiResult.Failure
        assertEquals(422, validationError.status)
        assertEquals("Invalid login", validationError.message)
    }

    @Test fun imageUrlHandlesRelativeAndAbsolutePaths() {
        assertEquals("https://anilibria.top/image.jpg", absoluteImageUrl("/image.jpg"))
        assertEquals("https://example.com/image.jpg", absoluteImageUrl("https://example.com/image.jpg"))
    }

    @Test fun timecodePostUsesSchemaAndSurfacesServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.saveTimecode("a2e76f6e-f3e7-418b-88a0-4ca7c1de8608", 73.5, false) is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("/api/v1/accounts/users/me/views/timecodes", request.path)
        val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonArray.first().jsonObject
        assertEquals("73.5", payload["time"].toString())
        assertEquals("false", payload["is_watched"].toString())
        assertEquals("\"a2e76f6e-f3e7-418b-88a0-4ca7c1de8608\"", payload["release_episode_id"].toString())
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        assertEquals(401, (repo.saveTimecode("id", 0.0, false) as ApiResult.Failure).status)
    }
}
