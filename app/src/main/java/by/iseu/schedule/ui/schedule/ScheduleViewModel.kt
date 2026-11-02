package by.iseu.schedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iseu.schedule.data.model.IseuException
import by.iseu.schedule.data.model.Lesson
import by.iseu.schedule.data.model.Mode
import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.model.ScheduleSelection
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

enum class ScheduleTab { SCHEDULE, TEACHERS }

data class ScheduleUiState(
    val tab: ScheduleTab = ScheduleTab.SCHEDULE,
    val selection: ScheduleSelection? = null,
    val teacherSelection: ScheduleSelection? = null,
    val teachers: List<Option> = emptyList(),
    val teacherQuery: String = "",
    val teachersLoading: Boolean = false,
    val teachersError: String? = null,
    val weekMonday: LocalDate = LocalDate.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val weeks: List<Option> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val siteMessage: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val isFromCache: Boolean = false,
    val fetchedAt: Instant? = null,
) {
    /** The target whose week is currently displayed. */
    val activeSelection: ScheduleSelection?
        get() = when (tab) {
            ScheduleTab.SCHEDULE -> selection
            ScheduleTab.TEACHERS -> teacherSelection
        }
}

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
    private var teachersJob: Job? = null

    init {
        viewModelScope.launch {
            repository.selectionFlow().filterNotNull().collectLatest { selection ->
                val previous = _state.value.selection
                val changed = previous != null && previous.cacheKey != selection.cacheKey
                if (previous != null && !changed) {
                    _state.update { it.copy(selection = selection) }
                    return@collectLatest
                }

                if (_state.value.tab == ScheduleTab.TEACHERS) {
                    _state.update { it.copy(selection = selection) }
                    return@collectLatest
                }

                val monday = WeekSelector.mondayOf(today())
                _state.update {
                    it.copy(
                        selection = selection,
                        weekMonday = monday,
                        selectedDay = defaultDay(monday),
                        weeks = emptyList(),
                        lessons = emptyList(),
                        siteMessage = null,
                        error = null,
                        isFromCache = false,
                        fetchedAt = null,
                    )
                }
                loadWeek(monday, force = false)
            }
        }
    }

    fun selectTab(tab: ScheduleTab) {
        if (_state.value.tab == tab) return
        loadJob?.cancel()
        _state.update {
            it.copy(
                tab = tab,
                weeks = emptyList(),
                lessons = emptyList(),
                siteMessage = null,
                error = null,
                isFromCache = false,
                fetchedAt = null,
                loading = false,
                refreshing = false,
            )
        }
        when (tab) {
            ScheduleTab.TEACHERS -> {
                loadTeachers()
                if (_state.value.teacherSelection != null) {
                    loadWeek(_state.value.weekMonday, force = false)
                }
            }

            ScheduleTab.SCHEDULE -> {
                if (_state.value.selection != null) {
                    loadWeek(_state.value.weekMonday, force = false)
                }
            }
        }
    }

    fun loadTeachers(force: Boolean = false) {
        if (!force && _state.value.teachers.isNotEmpty()) return
        teachersJob?.cancel()
        teachersJob = viewModelScope.launch {
            _state.update { it.copy(teachersLoading = true, teachersError = null) }
            try {
                val teachers = repository.loadTeachers(force)
                _state.update { it.copy(teachers = teachers, teachersLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = when (error) {
                    is IseuException ->
                        error.message.orEmpty().ifEmpty { "Не удалось загрузить список преподавателей" }

                    else -> "Не удалось загрузить список преподавателей"
                }
                _state.update { it.copy(teachersLoading = false, teachersError = message) }
            }
        }
    }

    fun reloadTeachers() {
        loadTeachers(force = true)
    }

    fun setTeacherQuery(query: String) {
        _state.update { it.copy(teacherQuery = query) }
    }

    fun selectTeacher(option: Option) {
        val monday = WeekSelector.mondayOf(today())
        loadJob?.cancel()
        _state.update {
            it.copy(
                tab = ScheduleTab.TEACHERS,
                teacherSelection = ScheduleSelection(
                    mode = Mode.TEACHER,
                    teacher = option,
                ),
                weekMonday = monday,
                selectedDay = defaultDay(monday),
                weeks = emptyList(),
                lessons = emptyList(),
                siteMessage = null,
                error = null,
                isFromCache = false,
                fetchedAt = null,
                loading = true,
                refreshing = false,
            )
        }
        loadWeek(monday, force = false)
    }

    fun closeTeacher() {
        if (_state.value.teacherSelection == null) return
        loadJob?.cancel()
        _state.update {
            it.copy(
                teacherSelection = null,
                weeks = emptyList(),
                lessons = emptyList(),
                siteMessage = null,
                error = null,
                isFromCache = false,
                fetchedAt = null,
                loading = false,
                refreshing = false,
            )
        }
    }

    fun refresh() {
        val current = _state.value
        if (current.tab == ScheduleTab.TEACHERS && current.teacherSelection == null) {
            loadTeachers(force = true)
        } else {
            loadWeek(current.weekMonday, force = true)
        }
    }

    fun retry() {
        refresh()
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

    private fun activeSelectionKey(): String? = _state.value.activeSelection?.cacheKey

    private fun loadWeek(monday: LocalDate, force: Boolean) {
        val selection = _state.value.activeSelection ?: run {
            _state.update { it.copy(loading = false, refreshing = false) }
            return
        }
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

            if (_state.value.weeks.isEmpty()) {
                val cachedWeeks = repository.cachedWeeks(selection)
                if (activeSelectionKey() == selection.cacheKey && cachedWeeks.isNotEmpty()) {
                    _state.update { it.copy(weeks = cachedWeeks) }
                }
            }
            if (activeSelectionKey() != selection.cacheKey) return@launch

            val cached = repository.cachedWeek(selection, monday).first()
            if (activeSelectionKey() != selection.cacheKey) return@launch
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
                if (activeSelectionKey() != selection.cacheKey) return@launch
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
                if (activeSelectionKey() != selection.cacheKey) return@launch
                if (error is IseuException.CatalogStale &&
                    selection.cacheKey == _state.value.selection?.cacheKey
                ) {
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
