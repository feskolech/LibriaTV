package ru.feskolech.libriatv.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.feskolech.libriatv.data.repo.ApiRepository
import ru.feskolech.libriatv.data.repo.ApiResult
import ru.feskolech.libriatv.domain.ScheduleItem

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    /** [days] is indexed 0 = Monday … 6 = Sunday. */
    data class Content(val days: List<List<ScheduleItem>>, val today: Int) : ScheduleUiState
    data class Error(val message: String) : ScheduleUiState
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {
    private val _state = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val state: StateFlow<ScheduleUiState> = _state

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = ScheduleUiState.Loading
        _state.value = when (val result = repository.scheduleWeek()) {
            is ApiResult.Failure -> ScheduleUiState.Error(result.message)
            is ApiResult.Success -> ScheduleUiState.Content(groupByDay(result.value), todayIndex())
        }
    }

    companion object {
        fun groupByDay(items: List<ScheduleItem>): List<List<ScheduleItem>> =
            (1..7).map { day -> items.filter { it.release.publishDayNumber == day }.sortedBy { it.release.title } }

        /** Calendar counts Sunday as 1; the API (and our tabs) start the week on Monday. */
        fun todayIndex(calendar: Calendar = Calendar.getInstance()): Int =
            (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
}
