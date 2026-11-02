package by.iseu.schedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iseu.schedule.data.model.IseuException
import by.iseu.schedule.data.model.Lesson
import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.model.ScheduleSelection
import by.iseu.schedule.data.parser.WeekSelector
import by.iseu.schedule.data.repo.ScheduleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class ScheduleUiState(
    val selection: ScheduleSelection? = null,
    val weekMonday: LocalDate = LocalDate.now(),
    val weeks: List<Option> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val siteMessage: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val isFromCache: Boolean = false,
    val fetchedAt: Instant? = null,
)

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScheduleUiState(weekMonday = WeekSelector.mondayOf(today())),
    )
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val selection = repository.selectionFlow().filterNotNull().first()
            _state.update { it.copy(selection = selection) }

            val cachedWeeks = repository.cachedWeeks(selection)
            if (cachedWeeks.isNotEmpty()) {
                _state.update { it.copy(weeks = cachedWeeks) }
            }

            loadWeek(_state.value.weekMonday, force = false)
        }
    }

    fun refresh() {
        loadWeek(_state.value.weekMonday, force = true)
    }

    fun retry() {
        loadWeek(_state.value.weekMonday, force = true)
    }

    fun previousWeek() {
        moveWeek(-1)
    }

    fun nextWeek() {
        moveWeek(1)
    }

    fun currentWeek() {
        loadWeek(targetWeek(WeekSelector.mondayOf(today())), force = false)
    }

    private fun moveWeek(delta: Int) {
        val state = _state.value
        val weeks = state.weeks.sortedBy { WeekSelector.parseOptionDate(it.label) ?: LocalDate.MAX }
        if (weeks.isEmpty()) {
            loadWeek(state.weekMonday.plusWeeks(delta.toLong()), force = false)
            return
        }

        val currentIndex = weeks.indexOfFirst {
            WeekSelector.parseOptionDate(it.label) == state.weekMonday
        }.takeIf { it >= 0 } ?: 0

        val newIndex = currentIndex + delta
        if (newIndex in weeks.indices) {
            WeekSelector.parseOptionDate(weeks[newIndex].label)?.let {
                loadWeek(it, force = false)
            }
        }
    }

    private fun targetWeek(monday: LocalDate): LocalDate {
        val option = WeekSelector.selectWeek(_state.value.weeks, monday) ?: return monday
        return WeekSelector.parseOptionDate(option.label) ?: monday
    }

    private fun loadWeek(monday: LocalDate, force: Boolean) {
        val selection = _state.value.selection ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val sameWeek = _state.value.weekMonday == monday
            val hasData = sameWeek && _state.value.lessons.isNotEmpty()

            _state.update {
                it.copy(
                    weekMonday = monday,
                    lessons = if (sameWeek) it.lessons else emptyList(),
                    siteMessage = if (sameWeek) it.siteMessage else null,
                    loading = !hasData,
                    refreshing = hasData,
                    error = null,
                )
            }

            val cached = repository.cachedWeek(selection, monday).first()
            if (cached != null) {
                _state.update {
                    it.copy(
                        weekMonday = cached.weekMonday,
                        lessons = cached.lessons,
                        siteMessage = cached.siteMessage,
                        isFromCache = true,
                        fetchedAt = cached.fetchedAt,
                    )
                }
            }

            try {
                val page = repository.loadWeek(selection, monday, force)
                _state.update {
                    it.copy(
                        weekMonday = page.schedule.weekMonday,
                        weeks = page.weeks.ifEmpty { it.weeks },
                        lessons = page.schedule.lessons,
                        siteMessage = page.schedule.siteMessage,
                        error = null,
                        isFromCache = false,
                        fetchedAt = page.schedule.fetchedAt,
                        loading = false,
                        refreshing = false,
                    )
                }
            } catch (error: Throwable) {
                val message = when (error) {
                    is IseuException.CatalogStale ->
                        "Сохранённая группа больше не доступна. Выберите заново."
                    is IseuException ->
                        error.message.orEmpty().ifEmpty { "Не удалось загрузить расписание" }
                    else -> "Не удалось загрузить расписание"
                }
                _state.update {
                    it.copy(
                        error = message,
                        loading = false,
                        refreshing = false,
                        isFromCache = cached != null || it.lessons.isNotEmpty(),
                    )
                }
            }
        }
    }
}
