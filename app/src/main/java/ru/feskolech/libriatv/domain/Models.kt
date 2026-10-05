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
    /** 1 = Monday … 7 = Sunday, as the API numbers release days. */
    val publishDayNumber: Int? = null,
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

/** A selectable catalog filter value: [id] goes to the API, [title] to the screen. */
data class FilterOption(val id: String, val title: String)

data class CatalogReferences(
    val genres: List<FilterOption>, val types: List<FilterOption>, val seasons: List<FilterOption>,
    val years: List<Int>, val sorting: List<FilterOption>, val statuses: List<FilterOption>,
)

data class CatalogFilter(
    val genres: Set<String> = emptySet(), val types: Set<String> = emptySet(),
    val seasons: Set<String> = emptySet(), val statuses: Set<String> = emptySet(),
    val fromYear: Int? = null, val toYear: Int? = null, val sorting: String? = null,
) {
    val activeCount: Int get() = listOf(genres, types, seasons, statuses).count { it.isNotEmpty() } +
        (if (fromYear != null || toYear != null) 1 else 0)
}

data class ReleasePage(val releases: List<Release>, val page: Int, val totalPages: Int)

/** User lists ("collections") of the AniLibria account. [apiValue] is what the API expects. */
enum class UserList(val apiValue: String) { WATCHING("WATCHING"), PLANNED("PLANNED"), WATCHED("WATCHED"), POSTPONED("POSTPONED"), ABANDONED("ABANDONED");
    companion object { fun of(value: String?) = entries.firstOrNull { it.apiValue == value } }
}
