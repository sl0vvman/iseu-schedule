package by.iseu.schedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iseu.schedule.data.model.IseuException
import by.iseu.schedule.data.model.Lesson
import by.iseu.schedule.data.model.Mode
import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.model.ScheduleSelection
import by.iseu.schedule.data.parser.TeacherMatcher
import by.iseu.schedule.data.parser.WeekSelector
import by.iseu.schedule.data.repo.ScheduleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class ScheduleUiState(
    val selection: ScheduleSelection? = null,
    val activeSelection: ScheduleSelection? = null,
    val isTeacherPreview: Boolean = false,
    val weekMonday: LocalDate = LocalDate.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val weeks: List<Option> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val siteMessage: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val isFromCache: Boolean = false,
    val fetchedAt: Instant? = null,
)

internal enum class ScheduleContent { LOADING, ERROR, EMPTY, LESSONS }

internal fun ScheduleUiState.content(): ScheduleContent = when {
    loading && lessons.isEmpty() -> ScheduleContent.LOADING
    error != null && !isFromCache && lessons.isEmpty() -> ScheduleContent.ERROR
    lessons.isEmpty() -> ScheduleContent.EMPTY
    else -> ScheduleContent.LESSONS
}

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScheduleUiState(
            weekMonday = WeekSelector.mondayOf(today()),
            selectedDay = today(),
        ),
    )
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.selectionFlow().filterNotNull().collectLatest { selection ->
                val previous = _state.value.selection
                val changed = previous != null && previous.cacheKey != selection.cacheKey
                if (previous != null && !changed) {
                    _state.update { it.copy(selection = selection) }
                    return@collectLatest
                }

                _state.update {
                    val targetMonday = if (changed || previous == null) {
                        WeekSelector.mondayOf(today())
                    } else {
                        it.weekMonday
                    }
                    it.copy(
                        selection = selection,
                        activeSelection = selection,
                        isTeacherPreview = false,
                        weekMonday = targetMonday,
                        selectedDay = if (changed || previous == null) {
                            defaultDay(targetMonday)
                        } else {
                            it.selectedDay
                        },
                        weeks = if (changed) emptyList() else it.weeks,
                        lessons = if (changed) emptyList() else it.lessons,
                        siteMessage = if (changed) null else it.siteMessage,
                        error = null,
                        notice = null,
                    )
                }

                if (changed || previous == null) {
                    val cachedWeeks = repository.cachedWeeks(selection)
                    if (cachedWeeks.isNotEmpty()) {
                        _state.update { it.copy(weeks = cachedWeeks) }
                    }
                }

                loadWeek(_state.value.weekMonday, force = false)
            }
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

    fun selectDay(date: LocalDate) {
        _state.update { it.copy(selectedDay = date) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    /**
     * Opens a teacher schedule from a group lesson without touching the saved
     * selection: the student can look up when to approach a teacher and then
     * return to their own group.
     */
    fun openTeacher(staff: String) {
        val current = _state.value
        val active = current.activeSelection ?: return
        if (active.mode != Mode.GROUP || current.isTeacherPreview) return

        viewModelScope.launch {
            _state.update { it.copy(notice = null) }
            val teachers = try {
                repository.loadTeachers()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update { it.copy(notice = "Не удалось загрузить список преподавателей") }
                return@launch
            }

            if (_state.value.activeSelection?.cacheKey != active.cacheKey ||
                _state.value.isTeacherPreview
            ) {
                return@launch
            }

            val teacher = TeacherMatcher.match(staff, teachers)
            if (teacher == null) {
                _state.update { it.copy(notice = "Преподаватель не найден в списке") }
                return@launch
            }

            val preview = ScheduleSelection(mode = Mode.TEACHER, teacher = teacher)
            _state.update {
                it.copy(
                    isTeacherPreview = true,
                    activeSelection = preview,
                    weeks = emptyList(),
                    lessons = emptyList(),
                    siteMessage = null,
                    error = null,
                    notice = null,
                )
            }
            repository.cachedWeeks(preview).takeIf { it.isNotEmpty() }?.let { cached ->
                _state.update { it.copy(weeks = cached) }
            }
            loadWeek(_state.value.weekMonday, force = false)
        }
    }

    fun closeTeacherPreview() {
        val saved = _state.value.selection ?: return
        if (!_state.value.isTeacherPreview) return
        _state.update {
            it.copy(
                isTeacherPreview = false,
                activeSelection = saved,
                weeks = emptyList(),
                lessons = emptyList(),
                siteMessage = null,
                error = null,
                notice = null,
            )
        }
        loadWeek(_state.value.weekMonday, force = false)
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

    private fun defaultDay(monday: LocalDate): LocalDate =
        if (WeekSelector.mondayOf(today()) == monday) today() else monday

    private fun adjustDay(selected: LocalDate, monday: LocalDate): LocalDate =
        if (selected.isBefore(monday) || selected.isAfter(monday.plusDays(6))) {
            defaultDay(monday)
        } else {
            selected
        }

    private fun loadWeek(monday: LocalDate, force: Boolean) {
        val selection = _state.value.activeSelection ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val sameWeek = _state.value.weekMonday == monday
            val hasData = sameWeek && _state.value.lessons.isNotEmpty()

            _state.update {
                it.copy(
                    weekMonday = monday,
                    selectedDay = if (sameWeek) it.selectedDay else defaultDay(monday),
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
                        selectedDay = adjustDay(it.selectedDay, cached.weekMonday),
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
                        selectedDay = adjustDay(it.selectedDay, page.schedule.weekMonday),
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (error is IseuException.CatalogStale && !_state.value.isTeacherPreview) {
                    runCatching { repository.clearSelection() }
                    runCatching { repository.clearCatalogCache() }
                }
                val message = when (error) {
                    is IseuException.CatalogStale ->
                        "Сохранённая группа или преподаватель больше не доступны. Выберите заново."
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
