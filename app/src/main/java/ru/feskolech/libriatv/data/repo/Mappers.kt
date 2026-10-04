package ru.feskolech.libriatv.data.repo

import ru.feskolech.libriatv.data.api.*
import ru.feskolech.libriatv.domain.*

fun absoluteImageUrl(path: String?): String? = when {
    path.isNullOrBlank() -> null
    path.startsWith("https://") || path.startsWith("http://") -> path
    path.startsWith("//") -> "https:$path"
    else -> "https://anilibria.top/${path.trimStart('/')}"
}

fun EpisodeDto.toDomain(): Episode? = id?.let {
    Episode(it, name.orEmpty(), ordinal, opening?.let { skip -> Skip(skip.start, skip.stop) },
        ending?.let { skip -> Skip(skip.start, skip.stop) },
        absoluteImageUrl(preview?.optimized?.preview ?: preview?.preview), hls480, hls720, hls1080)
}
fun ReleaseDto.toDomain(): Release? = id?.let {
    Release(it, name?.main.orEmpty(), alias, description,
        absoluteImageUrl(poster?.optimized?.preview ?: poster?.preview), freshAt,
        genres.mapNotNull(GenreDto::name), episodes.mapNotNull(EpisodeDto::toDomain), latestEpisode?.toDomain())
}
fun ScheduleItemDto.toDomain(): ScheduleItem? = release?.toDomain()?.let {
    ScheduleItem(it, publishedReleaseEpisode?.toDomain(), nextReleaseEpisodeNumber)
}
fun TorrentDto.toDomain(): Torrent? = id?.let { Torrent(it, label.orEmpty(), magnet, size, seeders) }
fun UserDto.toDomain(): User? = id?.let { User(it, nickname ?: login.orEmpty(), absoluteImageUrl(avatar?.preview)) }
