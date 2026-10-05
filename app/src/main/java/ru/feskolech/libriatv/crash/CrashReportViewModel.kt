package ru.feskolech.libriatv.crash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CrashReportViewModel @Inject constructor(private val reports: CrashReportManager) : ViewModel() {
    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt

    init {
        viewModelScope.launch {
            if (reports.pending()) {
                if (reports.automatic()) reports.sendPending() else _prompt.value = true
            }
        }
    }

    fun send(always: Boolean) {
        _prompt.value = false
        viewModelScope.launch {
            if (always) reports.setAutomatic(true)
            reports.sendPending()
        }
    }

    fun decline() {
        _prompt.value = false
        reports.discard()
    }
}
