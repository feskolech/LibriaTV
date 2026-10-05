package ru.feskolech.libriatv.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class UpdateCheckerTest {
    @Test fun apkDownloadHostsAreAllowlisted() {
        for (url in listOf("https://github.com/o/r/app.apk", "https://objects.githubusercontent.com/file.apk"))
            org.junit.Assert.assertTrue(url, allowedApkUrl(url))
        for (url in listOf("http://github.com/a.apk", "https://github.com.evil.test/a.apk",
            "https://evil.githubusercontent.com.test/a.apk", "https://api.github.com/a.apk"))
            org.junit.Assert.assertFalse(url, allowedApkUrl(url))
    }
    @Test fun comparesNumericVersionsWithoutTheVPrefix() {
        assertEquals(1, compareVersions("v1.10.0", "1.9.9"))
        assertEquals(-1, compareVersions("v1.2.3", "1.3.0"))
        assertEquals(0, compareVersions("v1.2.0", "1.2.0+build.4"))
        assertEquals(-1, compareVersions("v1.2.0-rc.2", "1.2.0"))
        assertEquals(1, compareVersions("v1.2.0-rc.10", "1.2.0-rc.2"))
        assertEquals(0, compareVersions("release-1", "1.0"))
    }

    @Test fun selectsFirstApkFromGithubReleaseFixture() {
        val fixture = javaClass.classLoader!!.getResource("api/github-release.json")!!.readText()
        val release = parseUpdateRelease(fixture)!!
        assertEquals("1.2.3", release.version)
        assertEquals("https://github.com/example/LibriaTV-1.2.3.apk", release.apkUrl)
        assertEquals("Исправлено воспроизведение и навигация.", release.notes)
    }

    @Test fun ignoresReleasesWithoutApk() {
        assertNull(parseUpdateRelease("""{"tag_name":"v2.0.0","assets":[]}"""))
    }

    @Test fun fetchesGithubReleaseWithoutAnAccountToken() {
        MockWebServer().use { server ->
            val fixture = javaClass.classLoader!!.getResource("api/github-release.json")!!.readText()
            server.enqueue(MockResponse().setBody(fixture).setResponseCode(200))
            val response = fetchLatestRelease(OkHttpClient(), server.url("/releases/latest").toString())
            assertEquals("1.2.3", (response as GithubReleaseResponse.Release).value.version)
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun treatsGithub404AsNoPublicRelease() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(GithubReleaseResponse.NotFound,
                fetchLatestRelease(OkHttpClient(), server.url("/releases/latest").toString()))
        }
    }

    @Test fun releaseNotesShowOnlyUiLanguageAsPlainText() {
        val body = "Первая **версия**\n- пункт `один`\n\n---\n\nFirst **release**"
        assertEquals("Первая версия\n• пункт один", releaseNotesForDisplay(body, "ru"))
        assertEquals("First release", releaseNotesForDisplay(body, "en"))
        assertEquals("Only one part", releaseNotesForDisplay("Only one part", "en"))
    }
}
