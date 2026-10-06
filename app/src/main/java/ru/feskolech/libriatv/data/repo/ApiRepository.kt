package ru.feskolech.libriatv.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import ru.feskolech.libriatv.data.api.ApiErrorDto
import retrofit2.HttpException
import ru.feskolech.libriatv.data.api.AniLibriaApi
import ru.feskolech.libriatv.data.api.ReferenceDto
import ru.feskolech.libriatv.data.api.TimecodeUpdateDto
import ru.feskolech.libriatv.data.api.EpisodeDto
import ru.feskolech.libriatv.domain.*
import javax.inject.Inject

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val status: Int?, val message: String) : ApiResult<Nothing>
}

class ApiRepository @Inject constructor(private val api: AniLibriaApi, private val legacy: LegacyCatalog) {
    private suspend fun <T> request(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: HttpException) {
        val body = error.response()?.errorBody()?.string()
        val parsed = body?.let { runCatching { Json.decodeFromString<ApiErrorDto>(it) }.getOrNull() }
        val message = parsed?.errors?.values?.flatten()?.firstOrNull() ?: parsed?.message ?: error.message()
        ApiResult.Failure(error.code(), message)
    } catch (error: Exception) {
        ApiResult.Failure(null, error.message ?: "Network error")
    }

    suspend fun scheduleNow(): ApiResult<List<ScheduleItem>> = request {
        api.scheduleNow().today.mapNotNull { it.toDomain() }
    }
    suspend fun currentSchedule(): ApiResult<CurrentSchedule> = request {
        api.scheduleNow().let { response ->
            CurrentSchedule(response.today.mapNotNull { it.toDomain() }, response.tomorrow.mapNotNull { it.toDomain() })
        }
    }
    suspend fun scheduleWeek(): ApiResult<List<ScheduleItem>> = request {
        api.scheduleWeek().mapNotNull { it.toDomain() }
    }
    suspend fun latest(limit: Int = 30): ApiResult<List<Release>> = request {
        api.latest(limit).mapNotNull { it.toDomain() }
    }
    suspend fun favoriteReleases(): ApiResult<List<Release>> = request {
        api.favoriteReleases(limit = 30).data.mapNotNull { it.toDomain() }
    }
    suspend fun release(idOrAlias: String): ApiResult<Release> {
        val result = request { api.release(idOrAlias).toDomain() ?: error("Release has no id") }
        // Hidden by the v1 API for this country (404/403): the legacy API may still serve it.
        if (result is ApiResult.Failure) idOrAlias.toIntOrNull()?.let { legacy.release(it) }?.let { return ApiResult.Success(it) }
        return result
    }
    suspend fun episode(id: String): ApiResult<Episode> = request {
        api.episode(id).toDomain() ?: error("Episode has no id")
    }
    suspend fun episodeDetails(id: String): ApiResult<EpisodeDto> = request { api.episode(id) }
    /** [compact] asks the API only for what a poster grid needs (~8x smaller pages). */
    suspend fun catalog(filter: CatalogFilter, page: Int, limit: Int = 30, compact: Boolean = false, fields: String? = null): ApiResult<ReleasePage> = request {
        val response = api.catalog(
            page = page, limit = limit,
            genres = filter.genres.takeIf { it.isNotEmpty() }?.joinToString(","),
            types = filter.types.toList().takeIf { it.isNotEmpty() },
            seasons = filter.seasons.toList().takeIf { it.isNotEmpty() },
            fromYear = filter.fromYear, toYear = filter.toYear,
            publishStatuses = filter.statuses.toList().takeIf { it.isNotEmpty() },
            sorting = filter.sorting,
            include = fields ?: if (compact) "id,name,poster,year,type" else null,
        )
        val pagination = response.meta?.pagination
        ReleasePage(response.data.mapNotNull { it.toDomain() }, pagination?.currentPage ?: page, pagination?.totalPages ?: page)
    }

    suspend fun catalogReferences(): ApiResult<CatalogReferences> = request {
        fun List<ReferenceDto>.options() = mapNotNull { ref ->
            ref.value?.let { FilterOption(it, ref.label ?: ref.description ?: it) }
        }
        coroutineScope {
            val genres = async { api.catalogGenres() }
            val types = async { api.catalogTypes() }
            val seasons = async { api.catalogSeasons() }
            val years = async { api.catalogYears() }
            val sorting = async { api.catalogSorting() }
            val statuses = async { api.catalogPublishStatuses() }
            CatalogReferences(
                genres = genres.await().mapNotNull { g -> g.id?.let { FilterOption(it.toString(), g.name.orEmpty()) } }
                    .sortedBy { it.title },
                types = types.await().map { FilterOption(it.value.orEmpty(), it.description ?: it.value.orEmpty()) },
                seasons = seasons.await().map { FilterOption(it.value.orEmpty(), it.description ?: it.value.orEmpty()) },
                years = years.await().sortedDescending(),
                sorting = sorting.await().options(),
                statuses = statuses.await().map { FilterOption(it.value.orEmpty(), it.description ?: it.value.orEmpty()) },
            )
        }
    }

    // The API rejects limit > 14 with 422.
    suspend fun recommended(limit: Int = 14, releaseId: Int? = null): ApiResult<List<Release>> = request {
        api.recommended(limit.coerceIn(1, 14), releaseId).mapNotNull { it.toDomain() }
    }

    suspend fun randomRelease(): ApiResult<Release> = request {
        api.random(1).firstNotNullOfOrNull { it.toDomain() } ?: error("No random release")
    }

    /** Search results with real title matches first (see [matchesQuery]). */
    suspend fun search(query: String): ApiResult<List<Release>> = when (val r = searchSplit(query)) {
        is ApiResult.Success -> ApiResult.Success(r.value.first + r.value.second)
        is ApiResult.Failure -> r
    }

    /** (titles matching the query, other fuzzy results), each in the API's order. */
    suspend fun searchSplit(query: String): ApiResult<Pair<List<Release>, List<Release>>> = coroutineScope {
        // The legacy search runs alongside: it also finds titles the v1 API hides in some countries.
        val old = async { legacy.search(query) }
        val result = request {
            val (match, rest) = api.search(query).partition { dto ->
                matchesQuery(query, listOf(dto.name?.main, dto.name?.english, dto.name?.alternative))
            }
            match.mapNotNull { it.toDomain() } to rest.mapNotNull { it.toDomain() }
        }
        val extra = old.await()
        when (result) {
            is ApiResult.Success -> {
                val known = (result.value.first + result.value.second).map { it.id }.toSet()
                // The legacy search matches titles strictly (all its names, incl. English), so its results are kept as is.
                val missing = extra.filter { it.id !in known }
                ApiResult.Success(mergeById(result.value.first, missing, extra) to result.value.second)
            }
            // v1 unreachable: the legacy results alone are still useful.
            is ApiResult.Failure -> if (extra.isNotEmpty()) ApiResult.Success(extra to emptyList()) else result
        }
    }
    suspend fun torrents(releaseId: Int): ApiResult<List<Torrent>> = request {
        api.torrents(releaseId).mapNotNull { it.toDomain() }
    }
    suspend fun saveTimecode(episodeId: String, seconds: Double, watched: Boolean): ApiResult<Unit> = request {
        val response = api.updateTimecodes(listOf(TimecodeUpdateDto(seconds, watched, episodeId)))
        if (!response.isSuccessful) throw HttpException(response)
    }
    suspend fun timecodes(): ApiResult<List<ServerTimecode>> = request {
        api.timecodes().mapNotNull { row ->
            if (row.size != 3) return@mapNotNull null
            val seconds = row[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val watched = row[2].jsonPrimitive.booleanOrNull ?: return@mapNotNull null
            ServerTimecode(row[0].jsonPrimitive.content, (seconds * 1000).toLong().coerceAtLeast(0), watched)
        }
    }
}

/**
 * Exact v1 matches plus legacy-only titles, ordered like the legacy search (which ranks the
 * franchise sensibly); titles it does not know keep their v1 position at the end.
 */
internal fun mergeById(v1: List<Release>, legacyOnly: List<Release>, legacyOrder: List<Release>): List<Release> {
    if (legacyOnly.isEmpty()) return v1
    val all = (v1 + legacyOnly).distinctBy { it.id }
    val rank = legacyOrder.mapIndexed { i, r -> r.id to i }.toMap()
    return all.sortedBy { rank[it.id] ?: (legacyOrder.size + all.indexOf(it)) }
}
