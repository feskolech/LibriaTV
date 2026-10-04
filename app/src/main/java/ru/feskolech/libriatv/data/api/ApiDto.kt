package ru.feskolech.libriatv.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ImageDto(val src: String? = null, val preview: String? = null, val thumbnail: String? = null, val optimized: ImageDto? = null)
@Serializable data class NameDto(val main: String? = null, val english: String? = null, val alternative: String? = null)
@Serializable data class ReferenceDto(val value: String? = null, val label: String? = null, val description: String? = null)
@Serializable data class GenreDto(val id: Int? = null, val name: String? = null, val image: ImageDto? = null, @SerialName("total_releases") val totalReleases: Int? = null)
@Serializable data class SiteRatingDto(val id: Int? = null, val url: String? = null, val votes: Int? = null, val rating: Double? = null)
@Serializable data class RatingDto(val average: Double? = null, val votes: Int? = null, val distribution: Map<String, Int>? = null)
@Serializable data class AgeRatingDto(val value: String? = null, val label: String? = null, @SerialName("is_adult") val isAdult: Boolean? = null, val description: String? = null)
@Serializable data class PublishDayDto(val value: Int? = null, val description: String? = null)
@Serializable data class MemberUserDto(val id: Int? = null, val avatar: ImageDto? = null)
@Serializable data class MemberDto(val id: String? = null, val role: ReferenceDto? = null, val user: MemberUserDto? = null, val nickname: String? = null)
@Serializable data class SponsorDto(val id: String? = null, val title: String? = null, val description: String? = null, @SerialName("url_title") val urlTitle: String? = null, val url: String? = null)
@Serializable data class TorrentMemberDto(val id: String? = null, val role: ReferenceDto? = null, val nickname: String? = null, @SerialName("external_url") val externalUrl: String? = null, val user: MemberUserDto? = null)
@Serializable data class SkipDto(val start: Double? = null, val stop: Double? = null)
@Serializable data class EpisodeDto(
    val id: String? = null, val name: String? = null, val ordinal: Double? = null,
    val opening: SkipDto? = null, val ending: SkipDto? = null, val preview: ImageDto? = null,
    @SerialName("hls_480") val hls480: String? = null,
    @SerialName("hls_720") val hls720: String? = null,
    @SerialName("hls_1080") val hls1080: String? = null,
    val duration: Double? = null, @SerialName("release_id") val releaseId: Int? = null,
    @SerialName("sort_order") val sortOrder: Double? = null,
    @SerialName("name_english") val nameEnglish: String? = null,
    @SerialName("rutube_id") val rutubeId: String? = null,
    @SerialName("youtube_id") val youtubeId: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val release: ReleaseDto? = null,
)
@Serializable data class ReleaseDto(
    val id: Int? = null, val type: ReferenceDto? = null, val year: Int? = null,
    val name: NameDto? = null, val alias: String? = null, val season: ReferenceDto? = null,
    val shikimori: SiteRatingDto? = null, val mal: SiteRatingDto? = null,
    val rating: RatingDto? = null,
    val poster: ImageDto? = null, @SerialName("fresh_at") val freshAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_ongoing") val isOngoing: Boolean? = null,
    @SerialName("age_rating") val ageRating: AgeRatingDto? = null,
    @SerialName("publish_day") val publishDay: PublishDayDto? = null,
    val description: String? = null, val notification: String? = null,
    @SerialName("episodes_total") val episodesTotal: Double? = null,
    @SerialName("external_player") val externalPlayer: String? = null,
    @SerialName("is_in_production") val isInProduction: Boolean? = null,
    @SerialName("is_blocked_by_geo") val isBlockedByGeo: Boolean? = null,
    @SerialName("is_blocked_by_copyrights") val isBlockedByCopyrights: Boolean? = null,
    @SerialName("added_in_users_favorites") val addedInUsersFavorites: Int? = null,
    @SerialName("average_duration_of_episode") val averageDurationOfEpisode: Double? = null,
    @SerialName("added_in_planned_collection") val addedInPlannedCollection: Int? = null,
    @SerialName("added_in_watched_collection") val addedInWatchedCollection: Int? = null,
    @SerialName("added_in_watching_collection") val addedInWatchingCollection: Int? = null,
    @SerialName("added_in_postponed_collection") val addedInPostponedCollection: Int? = null,
    @SerialName("added_in_abandoned_collection") val addedInAbandonedCollection: Int? = null,
    val genres: List<GenreDto> = emptyList(),
    val members: List<MemberDto> = emptyList(),
    val sponsors: List<SponsorDto> = emptyList(),
    val episodes: List<EpisodeDto> = emptyList(),
    @SerialName("latest_episode") val latestEpisode: EpisodeDto? = null,
    val torrents: List<TorrentDto> = emptyList(),
    @SerialName("background_covers") val backgroundCovers: List<ImageDto> = emptyList(),
)
@Serializable data class ScheduleItemDto(
    val release: ReleaseDto? = null,
    @SerialName("full_season_is_released") val fullSeasonIsReleased: Boolean? = null,
    @SerialName("published_release_episode") val publishedReleaseEpisode: EpisodeDto? = null,
    @SerialName("next_release_episode_number") val nextReleaseEpisodeNumber: Int? = null,
)
@Serializable data class ScheduleNowDto(val today: List<ScheduleItemDto> = emptyList(), val tomorrow: List<ScheduleItemDto> = emptyList(), val yesterday: List<ScheduleItemDto> = emptyList())
@Serializable data class ScheduleWeekDto(val data: List<ScheduleItemDto> = emptyList())
@Serializable data class TorrentDto(
    val id: Int? = null, val hash: String? = null, val size: Long? = null,
    val type: ReferenceDto? = null, val codec: ReferenceDto? = null,
    val color: ReferenceDto? = null, val quality: ReferenceDto? = null,
    val label: String? = null, val magnet: String? = null, val filename: String? = null,
    val seeders: Int? = null, val leechers: Int? = null, val bitrate: Int? = null,
    val description: String? = null, val release: ReleaseDto? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_hardsub") val isHardsub: Boolean? = null,
    @SerialName("completed_times") val completedTimes: Int? = null,
    @SerialName("torrent_members") val torrentMembers: List<TorrentMemberDto> = emptyList(),
)
@Serializable data class OtpGetRequestDto(@SerialName("device_id") val deviceId: String)
@Serializable data class OtpDto(val code: String? = null, @SerialName("user_id") val userId: Int? = null, @SerialName("device_id") val deviceId: String? = null, @SerialName("expired_at") val expiredAt: String? = null)
@Serializable data class OtpGetDto(val otp: OtpDto? = null, @SerialName("remaining_time") val remainingTime: Int? = null)
@Serializable data class OtpLoginRequestDto(val code: Int, @SerialName("device_id") val deviceId: String)
@Serializable data class LoginRequestDto(val login: String, val password: String)
@Serializable data class TokenDto(val token: String? = null)
@Serializable data class UserTorrentsDto(val passkey: String? = null, val uploaded: Long? = null, val downloaded: Long? = null)
@Serializable data class UserDto(val id: Int? = null, val login: String? = null, val email: String? = null, val nickname: String? = null, val avatar: ImageDto? = null, val torrents: UserTorrentsDto? = null, @SerialName("is_banned") val isBanned: Boolean? = null, @SerialName("created_at") val createdAt: String? = null, @SerialName("is_with_ads") val isWithAds: Boolean? = null)
@Serializable data class PaginationLinksDto(val previous: String? = null, val next: String? = null)
@Serializable data class PaginationDto(val total: Int? = null, val count: Int? = null, @SerialName("per_page") val perPage: Int? = null, @SerialName("current_page") val currentPage: Int? = null, @SerialName("total_pages") val totalPages: Int? = null, val links: PaginationLinksDto? = null)
@Serializable data class PaginationMetaDto(val pagination: PaginationDto? = null)
@Serializable data class FavoritesDto(val data: List<ReleaseDto> = emptyList(), val meta: PaginationMetaDto? = null)
@Serializable data class FavoriteUpdateDto(@SerialName("release_id") val releaseId: Int)
@Serializable data class TimecodeUpdateDto(val time: Double, @SerialName("is_watched") val isWatched: Boolean, @SerialName("release_episode_id") val releaseEpisodeId: String)
@Serializable data class ApiErrorDto(val message: String? = null, val errors: Map<String, List<String>>? = null)
