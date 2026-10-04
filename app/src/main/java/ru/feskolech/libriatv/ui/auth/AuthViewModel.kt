package ru.feskolech.libriatv.ui.auth

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.*
import javax.inject.Inject

data class AuthUiState(
    val auth: AuthState = AuthState.Loading,
    val code: String? = null,
    val secondsLeft: Int = 0,
    val loadingCode: Boolean = false,
    val expired: Boolean = false,
    val passwordMode: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state
    private var otpJob: Job? = null
    private var started = false
    /** Expired codes are renewed automatically a few times so a TV left on this screen stops polling eventually. */
    private var autoRenewals = 0

    init {
        viewModelScope.launch {
            repository.state.collect { auth ->
                _state.value = _state.value.copy(auth = auth)
                if (auth is AuthState.Authorized) otpJob?.cancel()
                if (started && auth is AuthState.Guest && _state.value.code == null && otpJob?.isActive != true) newCode()
            }
        }
    }

    fun start() {
        started = true
        if (_state.value.code == null && _state.value.auth is AuthState.Guest && otpJob?.isActive != true) newCode()
    }

    fun newCode() {
        autoRenewals = 0
        requestCode()
    }

    private fun requestCode() {
        otpJob?.cancel()
        otpJob = viewModelScope.launch {
            _state.value = _state.value.copy(code = null, loadingCode = true, expired = false, error = null)
            when (val result = repository.getOtp()) {
                is ApiResult.Failure -> _state.value = _state.value.copy(loadingCode = false, error = result.message)
                is ApiResult.Success -> {
                    val challenge = result.value
                    val deadline = SystemClock.elapsedRealtime() + (challenge.remainingSeconds * 1000).toLong()
                    _state.value = _state.value.copy(code = challenge.displayCode, loadingCode = false)
                    val ticker = launch {
                        while (true) {
                            val left = ((deadline - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
                            _state.value = _state.value.copy(secondsLeft = left)
                            delay(500)
                        }
                    }
                    try {
                        val success = OtpPolling(
                            poll = { repository.pollOtp(challenge) },
                            nowMs = SystemClock::elapsedRealtime,
                        ).run(deadline) { message -> _state.value = _state.value.copy(error = message) }
                        if (!success) _state.value = _state.value.copy(expired = true, secondsLeft = 0)
                    } finally { ticker.cancel() }
                    if (_state.value.expired && autoRenewals < MAX_AUTO_RENEWALS) {
                        autoRenewals++
                        requestCode()
                    }
                }
            }
        }
    }

    fun showPassword(show: Boolean) { _state.value = _state.value.copy(passwordMode = show, error = null) }

    fun login(login: String, password: String) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null)
            when (val result = repository.login(login, password)) {
                is ApiResult.Success -> _state.value = _state.value.copy(submitting = false, passwordMode = false)
                is ApiResult.Failure -> _state.value = _state.value.copy(submitting = false, error = result.message)
            }
        }
    }

    private companion object {
        const val MAX_AUTO_RENEWALS = 3
    }

    fun logout() { viewModelScope.launch { repository.logout(); newCode() } }
    fun retryProfile() { viewModelScope.launch { repository.refreshProfile() } }
}
