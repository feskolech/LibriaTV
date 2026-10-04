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
)
data class ScheduleItem(val release: Release, val publishedEpisode: Episode?, val nextEpisodeNumber: Int?)
data class Torrent(val id: Int, val label: String, val magnet: String?, val size: Long?, val seeders: Int?)
data class User(val id: Int, val nickname: String, val avatarUrl: String?)
