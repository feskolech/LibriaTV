package ru.feskolech.libriatv.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import ru.feskolech.libriatv.BuildConfig
import ru.feskolech.libriatv.data.api.AniLibriaApi
import ru.feskolech.libriatv.data.repo.TokenStore
import ru.feskolech.libriatv.data.repo.SettingsStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides @Singleton fun json(): Json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Provides @Singleton fun client(tokenStore: TokenStore, settingsStore: SettingsStore,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): OkHttpClient {
        val auth = Interceptor { chain ->
            val token = runBlocking { tokenStore.get() }
            val preferred = runBlocking { settingsStore.mirror() }
            val original = chain.request()
            // The same client also fetches HLS playlists/segments from CDN hosts: the account token and
            // the 401 logout only apply to the AniLibria API itself.
            val isApi = original.url.host == "anilibria.top" || original.url.host == "aniliberty.top"
            val apiRequest = if (isApi)
                original.newBuilder().url(original.url.newBuilder().host(preferred).build()).build() else original
            val request = apiRequest.newBuilder()
                .header("User-Agent", "LibriaTV/${BuildConfig.VERSION_NAME}")
                .apply {
                    if (isApi) {
                        header("mobileApp", "true")
                        // Match the official app-tv client headers for AniLibria API requests.
                        header("App-Id", "ru.radiationx.anilibria.app.tv")
                        header("App-Ver-Name", "1.3.2")
                        header("App-Ver-Code", "8")
                        header("User-Agent", "mobileApp Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.170 Safari/537.36 OPR/53.0.2907.68")
                        if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
                    }
                }
                .build()
            val response = try {
                chain.proceed(request)
            } catch (error: IOException) {
                if (request.url.host != "anilibria.top" && request.url.host != "aniliberty.top") throw error
                val fallback = if (request.url.host == "anilibria.top") "aniliberty.top" else "anilibria.top"
                chain.proceed(request.newBuilder().url(request.url.newBuilder().host(fallback).build()).build())
            }
            if (isApi && response.code == 401) runBlocking { tokenStore.set(null) }
            response
        }
        // Path + status + duration only (no query, headers or body): safe in release, used to diagnose
        // slow screens on real TV boxes via `adb logcat -s LibriaNet`.
        val timing = Interceptor { chain ->
            val started = System.nanoTime()
            val request = chain.request()
            try {
                chain.proceed(request).also {
                    android.util.Log.i("LibriaNet", "${it.code} ${(System.nanoTime() - started) / 1_000_000} ms ${request.method} ${request.url.host}${request.url.encodedPath}")
                }
            } catch (e: IOException) {
                android.util.Log.w("LibriaNet", "FAIL ${(System.nanoTime() - started) / 1_000_000} ms ${request.method} ${request.url.host}${request.url.encodedPath}: ${e.javaClass.simpleName}")
                throw e
            }
        }
        // Per-phase timings (DNS / connect / TLS / waiting for a connection) for the same diagnostics.
        val phases = okhttp3.EventListener.Factory { call ->
            val t0 = System.nanoTime()
            fun log(phase: String) { android.util.Log.i("LibriaNet", "+${(System.nanoTime() - t0) / 1_000_000} ms $phase ${call.request().url.encodedPath}") }
            object : okhttp3.EventListener() {
                override fun dnsStart(call: okhttp3.Call, domainName: String) { log("dnsStart") }
                override fun dnsEnd(call: okhttp3.Call, domainName: String, inetAddressList: List<java.net.InetAddress>) { log("dnsEnd ${inetAddressList.joinToString { it.hostAddress.orEmpty() }}") }
                override fun connectStart(call: okhttp3.Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) { log("connectStart ${inetSocketAddress.address.hostAddress}") }
                override fun secureConnectStart(call: okhttp3.Call) { log("tlsStart") }
                override fun secureConnectEnd(call: okhttp3.Call, handshake: okhttp3.Handshake?) { log("tlsEnd") }
                override fun connectFailed(call: okhttp3.Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy, protocol: okhttp3.Protocol?, ioe: IOException) { log("connectFailed ${inetSocketAddress.address.hostAddress} ${ioe.javaClass.simpleName}") }
                override fun connectionAcquired(call: okhttp3.Call, connection: okhttp3.Connection) { log("connectionAcquired") }
                override fun responseHeadersStart(call: okhttp3.Call) { log("responseHeadersStart") }
            }
        }
        return OkHttpClient.Builder()
            .eventListenerFactory(phases)
            .addInterceptor(timing)
            .addInterceptor(auth)
            .apply { if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }) }
            .dns(ru.feskolech.libriatv.data.api.FallbackDns(context))
            // 8 s per address: Cloudflare gives two, so a dead one costs 8 s, not 15.
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton fun api(client: OkHttpClient, json: Json): AniLibriaApi = Retrofit.Builder()
        .baseUrl("https://anilibria.top/api/v1/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(AniLibriaApi::class.java)
}
