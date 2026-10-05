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

    @Provides @Singleton fun client(tokenStore: TokenStore, settingsStore: SettingsStore): OkHttpClient {
        val auth = Interceptor { chain ->
            val token = runBlocking { tokenStore.get() }
            val preferred = runBlocking { settingsStore.mirror() }
            val original = chain.request()
            val apiRequest = if (original.url.host == "anilibria.top" || original.url.host == "aniliberty.top")
                original.newBuilder().url(original.url.newBuilder().host(preferred).build()).build() else original
            val request = apiRequest.newBuilder()
                .header("User-Agent", "LibriaTV/${BuildConfig.VERSION_NAME}")
                .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                .build()
            val response = try {
                chain.proceed(request)
            } catch (error: IOException) {
                if (request.url.host != "anilibria.top" && request.url.host != "aniliberty.top") throw error
                val fallback = if (request.url.host == "anilibria.top") "aniliberty.top" else "anilibria.top"
                chain.proceed(request.newBuilder().url(request.url.newBuilder().host(fallback).build()).build())
            }
            if (response.code == 401) runBlocking { tokenStore.set(null) }
            response
        }
        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .apply { if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }) }
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton fun api(client: OkHttpClient, json: Json): AniLibriaApi = Retrofit.Builder()
        .baseUrl("https://anilibria.top/api/v1/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(AniLibriaApi::class.java)
}
