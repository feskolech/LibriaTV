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

class ApiRepository @Inject constructor(private val api: AniLibriaApi) {
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
    suspend fun release(idOrAlias: String): ApiResult<Release> = request {
        api.release(idOrAlias).toDomain() ?: error("Release has no id")
    }
    suspend fun episode(id: String): ApiResult<Episode> = request {
        api.episode(id).toDomain() ?: error("Episode has no id")
    }
    suspend fun episodeDetails(id: String): ApiResult<EpisodeDto> = request { api.episode(id) }
    suspend fun catalog(filter: CatalogFilter, page: Int, limit: Int = 30): ApiResult<ReleasePage> = request {
        val response = api.catalog(
            page = page, limit = limit,
            genres = filter.genres.takeIf { it.isNotEmpty() }?.joinToString(","),
            types = filter.types.toList().takeIf { it.isNotEmpty() },
            seasons = filter.seasons.toList().takeIf { it.isNotEmpty() },
            fromYear = filter.fromYear, toYear = filter.toYear,
            publishStatuses = filter.statuses.toList().takeIf { it.isNotEmpty() },
            sorting = filter.sorting,
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
    suspend fun recommended(limit: Int = 14): ApiResult<List<Release>> = request {
        api.recommended(limit).mapNotNull { it.toDomain() }
    }

    suspend fun randomRelease(): ApiResult<Release> = request {
        api.random(1).firstNotNullOfOrNull { it.toDomain() } ?: error("No random release")
    }

    suspend fun search(query: String): ApiResult<List<Release>> = request {
        api.search(query).mapNotNull { it.toDomain() }
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
