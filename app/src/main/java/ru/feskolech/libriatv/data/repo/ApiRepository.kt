package ru.feskolech.libriatv.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import ru.feskolech.libriatv.data.api.ApiErrorDto
import retrofit2.HttpException
import ru.feskolech.libriatv.data.api.AniLibriaApi
import ru.feskolech.libriatv.data.api.TimecodeUpdateDto
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
}
