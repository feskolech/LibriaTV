package ru.feskolech.libriatv.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import ru.feskolech.libriatv.data.api.*
import ru.feskolech.libriatv.domain.User
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Loading : AuthState
    data object Guest : AuthState
    data class Authorized(val user: User) : AuthState
    data class Error(val message: String) : AuthState
}

data class OtpChallenge(val displayCode: String, val loginCode: Int, val remainingSeconds: Double)

fun OtpGetDto.toChallenge(): OtpChallenge {
    val code = otp?.code ?: error("OTP code is missing")
    require(code.length == 6 && code.all(Char::isDigit)) { "Invalid OTP code" }
    return OtpChallenge(code, code.toInt(), remainingTime ?: 0.0)
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: AniLibriaApi,
    private val tokenStore: TokenStore,
    private val deviceIdStore: DeviceIdStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state

    init {
        scope.launch {
            tokenStore.token.collectLatest { token ->
                if (token.isNullOrBlank()) _state.value = AuthState.Guest
                else refreshProfile()
            }
        }
    }

    private suspend fun <T> request(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: HttpException) {
        val body = error.response()?.errorBody()?.string()
        val parsed = body?.let { runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<ApiErrorDto>(it) }.getOrNull() }
        ApiResult.Failure(error.code(), parsed?.errors?.values?.flatten()?.firstOrNull() ?: parsed?.message ?: error.message())
    } catch (error: Exception) {
        ApiResult.Failure(null, error.message ?: "Network error")
    }

    suspend fun getOtp(): ApiResult<OtpChallenge> = request {
        api.otpGet(OtpGetRequestDto(deviceIdStore.get())).toChallenge()
    }

    suspend fun pollOtp(challenge: OtpChallenge): ApiResult<Boolean> = request {
        try {
            val token = api.otpLogin(OtpLoginRequestDto(challenge.loginCode, deviceIdStore.get())).token
                ?: error("Token is missing")
            tokenStore.set(token)
            true
        } catch (error: HttpException) {
            if (error.code() == 401 || error.code() == 404) false else throw error
        }
    }

    suspend fun login(login: String, password: String): ApiResult<Unit> = request {
        val token = api.login(LoginRequestDto(login, password)).token ?: error("Token is missing")
        tokenStore.set(token)
    }

    suspend fun logout() {
        try { api.logout() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* local sign out still applies */ }
        tokenStore.set(null)
        _state.value = AuthState.Guest
    }

    /**
     * Profile check for background work: same request, but a network failure does not flip the
     * app-wide state to Error (the UI would show it). Returns the user id, null for a guest, or
     * throws [java.io.IOException] when the answer is unknown.
     */
    suspend fun currentUserIdForBackground(): Int? {
        (state.value as? AuthState.Authorized)?.let { return it.user.id }
        return when (val result = request { api.profile().toDomain() ?: error("User id is missing") }) {
            is ApiResult.Success -> { _state.value = AuthState.Authorized(result.value); result.value.id }
            is ApiResult.Failure -> if (result.status == 401) null else throw java.io.IOException(result.message)
        }
    }

    suspend fun refreshProfile() {
        when (val result = request { api.profile().toDomain() ?: error("User id is missing") }) {
            is ApiResult.Success -> _state.value = AuthState.Authorized(result.value)
            is ApiResult.Failure -> _state.value = if (result.status == 401) AuthState.Guest else AuthState.Error(result.message)
        }
    }
}
