package ru.feskolech.libriatv.data.api

import kotlinx.serialization.json.JsonArray
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AniLibriaApi {
    @GET("anime/schedule/now") suspend fun scheduleNow(): ScheduleNowDto
    @GET("anime/schedule/week") suspend fun scheduleWeek(): List<ScheduleItemDto>
    @GET("anime/releases/latest") suspend fun latest(@Query("limit") limit: Int = 30): List<ReleaseDto>
    @GET("anime/releases/recommended") suspend fun recommended(@Query("limit") limit: Int = 14): List<ReleaseDto>
    @GET("anime/releases/random") suspend fun random(@Query("limit") limit: Int = 1): List<ReleaseDto>
    @GET("anime/releases/{idOrAlias}") suspend fun release(@Path("idOrAlias") idOrAlias: String): ReleaseDto
    @GET("anime/releases/episodes/{releaseEpisodeId}") suspend fun episode(@Path("releaseEpisodeId") id: String): EpisodeDto
    @GET("anime/catalog/releases") suspend fun catalog(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("f[genres]") genres: String? = null,
        @Query("f[types][]") types: List<String>? = null,
        @Query("f[seasons][]") seasons: List<String>? = null,
        @Query("f[years][from_year]") fromYear: Int? = null,
        @Query("f[years][to_year]") toYear: Int? = null,
        @Query("f[publish_statuses][]") publishStatuses: List<String>? = null,
        @Query("f[sorting]") sorting: String? = null,
    ): FavoritesDto
    @GET("anime/catalog/references/genres") suspend fun catalogGenres(): List<GenreDto>
    @GET("anime/catalog/references/types") suspend fun catalogTypes(): List<ReferenceDto>
    @GET("anime/catalog/references/seasons") suspend fun catalogSeasons(): List<ReferenceDto>
    @GET("anime/catalog/references/years") suspend fun catalogYears(): List<Int>
    @GET("anime/catalog/references/sorting") suspend fun catalogSorting(): List<ReferenceDto>
    @GET("anime/catalog/references/publish-statuses") suspend fun catalogPublishStatuses(): List<ReferenceDto>
    @GET("app/search/releases") suspend fun search(@Query("query") query: String): List<ReleaseDto>
    @GET("anime/torrents/release/{releaseId}") suspend fun torrents(@Path("releaseId") releaseId: Int): List<TorrentDto>
    @POST("accounts/otp/get") suspend fun otpGet(@Body request: OtpGetRequestDto): OtpGetDto
    @POST("accounts/otp/login") suspend fun otpLogin(@Body request: OtpLoginRequestDto): TokenDto
    @POST("accounts/users/auth/login") suspend fun login(@Body request: LoginRequestDto): TokenDto
    @POST("accounts/users/auth/logout") suspend fun logout(): TokenDto
    @GET("accounts/users/me/profile") suspend fun profile(): UserDto
    @GET("accounts/users/me/favorites/ids") suspend fun favoriteIds(): List<Int>
    @GET("accounts/users/me/favorites/releases") suspend fun favoriteReleases(@Query("page") page: Int = 1, @Query("limit") limit: Int = 15, @Query("f[sorting]") sorting: String? = null): FavoritesDto
    @GET("accounts/users/me/favorites/references/sorting") suspend fun favoriteSorting(): List<ReferenceDto>
    @POST("accounts/users/me/favorites") suspend fun addFavorites(@Body releases: List<FavoriteUpdateDto>): List<Int>
    @HTTP(method = "DELETE", path = "accounts/users/me/favorites", hasBody = true)
    suspend fun deleteFavorites(@Body releases: List<FavoriteUpdateDto>): List<Int>
    @GET("accounts/users/me/views/timecodes") suspend fun timecodes(@Query("since") since: String? = null): List<JsonArray>
    @POST("accounts/users/me/views/timecodes") suspend fun updateTimecodes(@Body timecodes: List<TimecodeUpdateDto>): retrofit2.Response<Unit>
}
