package ru.feskolech.libriatv.data.repo

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import ru.feskolech.libriatv.data.api.AniLibriaApi
import ru.feskolech.libriatv.data.api.OtpGetRequestDto
import ru.feskolech.libriatv.data.api.OtpLoginRequestDto

class AuthFlowTest {
    @Test fun otpCodeKeepsLeadingZeroOnScreenAndSendsInteger() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/api/v1/"))
                .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = false }
                    .asConverterFactory("application/json".toMediaType()))
                .build().create(AniLibriaApi::class.java)
            val fixture = javaClass.classLoader!!.getResourceAsStream("api/otp-get.json")!!
                .bufferedReader().use { it.readText() }
            server.enqueue(MockResponse().setBody(fixture))
            val challenge = api.otpGet(OtpGetRequestDto("test-device")).toChallenge()
            assertEquals("058701", challenge.displayCode)
            assertEquals(58701, challenge.loginCode)
            assertTrue(challenge.remainingSeconds > 299)
            server.takeRequest()
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            runCatching { api.otpLogin(OtpLoginRequestDto(challenge.loginCode, "test-device")) }
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"code\":58701"))
            assertTrue(body.contains("\"device_id\":\"test-device\""))
        } finally { server.shutdown() }
    }

    @Test fun pollingSucceedsAfterPendingResponse() = runBlocking {
        var time = 0L
        var calls = 0
        val success = OtpPolling(
            poll = { calls++; ApiResult.Success(calls == 2) },
            intervalMs = 4,
            nowMs = { time },
            waitMs = { time += it },
        ).run(20) { fail(it) }
        assertTrue(success)
        assertEquals(2, calls)
    }

    @Test fun pollingExpiresAtDeadline() = runBlocking {
        var time = 0L
        var calls = 0
        val success = OtpPolling(
            poll = { calls++; ApiResult.Success(false) },
            intervalMs = 4,
            nowMs = { time },
            waitMs = { time += it },
        ).run(10) { fail(it) }
        assertFalse(success)
        assertEquals(2, calls)
    }

    @Test fun pollingRecoversFromNetworkError() = runBlocking {
        var time = 0L
        var calls = 0
        var errors = 0
        val success = OtpPolling(
            poll = { calls++; if (calls == 1) ApiResult.Failure(null, "offline") else ApiResult.Success(true) },
            intervalMs = 4,
            nowMs = { time },
            waitMs = { time += it },
        ).run(20) { errors++; assertEquals("offline", it) }
        assertTrue(success)
        assertEquals(1, errors)
    }
}
