package ru.feskolech.libriatv.data.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiAuthInterceptorTest {
    private fun client(token: String?, mirror: String, unauthorized: () -> Unit = {}) = OkHttpClient.Builder()
        .addInterceptor(ApiAuthInterceptor({ token }, { mirror }, unauthorized, "LibriaTV/test",
            apiHosts = setOf("localhost", "127.0.0.1")))
        .build()

    @Test fun apiRequestGoesToMirrorWithToken() {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            server.enqueue(MockResponse().setBody("{}"))
            client("secret", mirror = "127.0.0.1")
                .newCall(Request.Builder().url("http://localhost:${server.port}/api/v1/x").build())
                .execute().close()
            val sent = server.takeRequest()
            assertEquals("127.0.0.1:${server.port}", sent.getHeader("Host"))
            assertEquals("Bearer secret", sent.getHeader("Authorization"))
        }
    }

    @Test fun nonApiHostGetsNoToken() {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            server.enqueue(MockResponse().setBody("seg"))
            OkHttpClient.Builder()
                .addInterceptor(ApiAuthInterceptor({ "secret" }, { "localhost" }, {}, "LibriaTV/test", apiHosts = setOf("localhost")))
                .build()
                .newCall(Request.Builder().url("http://127.0.0.1:${server.port}/seg.ts").build())
                .execute().close()
            val sent = server.takeRequest()
            assertNull(sent.getHeader("Authorization"))
            assertEquals("LibriaTV/test", sent.getHeader("User-Agent"))
        }
    }

    @Test fun unauthorizedIsReported() {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            server.enqueue(MockResponse().setResponseCode(401))
            var reported = false
            client("old", mirror = "localhost") { reported = true }
                .newCall(Request.Builder().url("http://localhost:${server.port}/api/v1/me").build()).execute().close()
            assertEquals(true, reported)
        }
    }
}
