package ru.feskolech.libriatv.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import ru.feskolech.libriatv.data.api.ApiErrorDto

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * The one place that turns an API call into [ApiResult]: HTTP errors keep their status and the
 * server's message when it sends one; cancellation is never swallowed.
 */
internal suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: HttpException) {
    val body = error.response()?.errorBody()?.string()
    val parsed = body?.let { runCatching { errorJson.decodeFromString<ApiErrorDto>(it) }.getOrNull() }
    ApiResult.Failure(error.code(), parsed?.errors?.values?.flatten()?.firstOrNull() ?: parsed?.message ?: error.message())
} catch (error: Exception) {
    ApiResult.Failure(null, error.message ?: "Network error")
}
