package ru.feskolech.libriatv.domain

data class Skip(val start: Double?, val stop: Double?)
data class Episode(
    val id: String, val name: String, val ordinal: Double?,
    val opening: Skip?, val ending: Skip?, val previewUrl: String?,
    val hls480: String?, val hls720: String?, val hls1080: String?,
)
data class Release(
    val id: Int, val title: String, val alias: String?, val description: String?,
    val posterUrl: String?, val freshAt: String?, val genres: List<String>,
    val episodes: List<Episode>, val latestEpisode: Episode?,
    val year: Int? = null, val type: String? = null, val season: String? = null,
    val publishDay: String? = null, val isOngoing: Boolean? = null,
    val episodesTotal: Double? = null,
)
data class ScheduleItem(val release: Release, val publishedEpisode: Episode?, val nextEpisodeNumber: Int?)
data class CurrentSchedule(val today: List<ScheduleItem>, val tomorrow: List<ScheduleItem>)
data class Torrent(
    val id: Int, val label: String, val magnet: String?, val size: Long?, val seeders: Int?,
    val leechers: Int? = null, val episodes: String? = null, val quality: String? = null,
    val codec: String? = null, val type: String? = null, val isHardsub: Boolean = false,
    val createdAt: String? = null,
)
data class User(val id: Int, val nickname: String, val avatarUrl: String?)
