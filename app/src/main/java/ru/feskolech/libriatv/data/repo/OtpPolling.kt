package ru.feskolech.libriatv.data.repo

import kotlinx.coroutines.delay

/** Keeps transient network failures from discarding a still valid device code. */
class OtpPolling(
    private val poll: suspend () -> ApiResult<Boolean>,
    private val intervalMs: Long = 4_000,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val waitMs: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun run(deadlineMs: Long, onNetworkError: (String) -> Unit): Boolean {
        while (nowMs() < deadlineMs) {
            waitMs(minOf(intervalMs, deadlineMs - nowMs()))
            if (nowMs() >= deadlineMs) break
            when (val result = poll()) {
                is ApiResult.Success -> if (result.value) return true
                is ApiResult.Failure -> if (result.status == null) onNetworkError(result.message)
            }
        }
        return false
    }
}
