package ru.feskolech.libriatv.data.repo

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import ru.feskolech.libriatv.data.api.AniLibriaApi

class FavoritesRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: FavoritesRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder().baseUrl(server.url("/api/v1/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = false }
                .asConverterFactory("application/json".toMediaType()))
            .build().create(AniLibriaApi::class.java)
        repository = FavoritesRepository(api)
    }

    @After fun tearDown() { server.shutdown() }

    private fun enqueue(body: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
    }

    @Test fun idsAndToggleUseServerStateAndRollbackOnError() = runBlocking {
        enqueue("[10306]")
        assertEquals(setOf(10306), (repository.refreshIds() as ApiResult.Success).value)
        assertEquals("/api/v1/accounts/users/me/favorites/ids", server.takeRequest().path)

        enqueue("[10306,10307]")
        assertEquals(setOf(10306, 10307), (repository.toggle(10307) as ApiResult.Success).value)
        val add = server.takeRequest()
        assertEquals("POST", add.method)
        assertEquals("/api/v1/accounts/users/me/favorites", add.path)
        assertEquals("[{\"release_id\":10307}]", add.body.readUtf8())

        enqueue("{}", 503)
        assertTrue(repository.toggle(10306) is ApiResult.Failure)
        assertEquals(setOf(10306, 10307), repository.ids.value)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("[{\"release_id\":10306}]", delete.body.readUtf8())
    }

    @Test fun pagesAndSortingFollowOpenApiSchema() = runBlocking {
        val first = javaClass.classLoader!!.getResourceAsStream("api/favorites-page.json")!!
            .bufferedReader().use { it.readText() }
        enqueue(first)
        val page = (repository.releases(1, "RATING_DESC") as ApiResult.Success).value
        assertEquals(2, page.totalPages)
        assertEquals(10306, page.releases.single().id)
        assertEquals(7, page.releases.single().latestEpisode?.ordinal?.toInt())
        val path = java.net.URLDecoder.decode(server.takeRequest().path!!, "UTF-8")
        assertTrue(path, "f[sorting]=RATING_DESC" in path)

        enqueue("[{\"value\":\"RATING_DESC\",\"label\":\"По рейтингу\"}]")
        assertEquals("По рейтингу", (repository.sorting() as ApiResult.Success).value.single().title)
        assertEquals("/api/v1/accounts/users/me/favorites/references/sorting", server.takeRequest().path)
    }

    @Test fun startupFetchesAllPagesBeforeComparing() = runBlocking {
        val first = javaClass.classLoader!!.getResourceAsStream("api/favorites-page.json")!!
            .bufferedReader().use { it.readText() }
        enqueue(first)
        enqueue("{\"data\":[{\"id\":10307,\"name\":{\"main\":\"Второй\"},\"latest_episode\":{\"id\":\"e8\",\"ordinal\":8}}],\"meta\":{\"pagination\":{\"current_page\":2,\"total_pages\":2}}}")
        val releases = (repository.allReleases() as ApiResult.Success).value
        assertEquals(listOf(10306, 10307), releases.map { it.id })
        assertTrue(server.takeRequest().path!!.contains("page=1"))
        assertTrue(server.takeRequest().path!!.contains("page=2"))
    }
}
