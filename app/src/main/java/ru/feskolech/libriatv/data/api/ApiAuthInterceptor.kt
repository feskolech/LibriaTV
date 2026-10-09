package ru.feskolech.libriatv.data.api

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * AniLibria API requests only (the same client also fetches HLS playlists and segments from CDN
 * hosts): routes them to the chosen mirror, falls back to the other mirror on a network error,
 * adds the app headers and the account token, and reports a 401 so the token can be dropped.
 * The token and mirror are read from in-memory values, never from storage on the request thread.
 */
class ApiAuthInterceptor(
    private val token: () -> String?,
    private val mirror: () -> String,
    private val onUnauthorized: () -> Unit,
    private val userAgent: String,
    private val apiHosts: Set<String> = API_HOSTS,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host !in apiHosts) {
            return chain.proceed(original.newBuilder().header("User-Agent", userAgent).build())
        }
        val bearer = token()
        val request = original.newBuilder()
            .url(original.url.newBuilder().host(mirror()).build())
            .header("mobileApp", "true")
            // Match the official app-tv client headers for AniLibria API requests.
            .header("App-Id", "ru.radiationx.anilibria.app.tv")
            .header("App-Ver-Name", "1.3.2")
            .header("App-Ver-Code", "8")
            .header("User-Agent", "mobileApp Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.170 Safari/537.36 OPR/53.0.2907.68")
            .apply { if (!bearer.isNullOrBlank()) header("Authorization", "Bearer $bearer") }
            .build()
        val response = try {
            chain.proceed(request)
        } catch (error: IOException) {
            val fallback = apiHosts.firstOrNull { it != request.url.host } ?: throw error
            chain.proceed(request.newBuilder().url(request.url.newBuilder().host(fallback).build()).build())
        }
        if (response.code == 401) onUnauthorized()
        return response
    }

    companion object {
        val API_HOSTS = setOf("aniliberty.top", "anilibria.top")
    }
}
