package by.iseu.schedule.ui.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iseu.schedule.data.model.IseuException
import by.iseu.schedule.data.model.Mode
import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.model.ScheduleSelection
import by.iseu.schedule.data.repo.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SelectUiState(
    val mode: Mode = Mode.GROUP,
    val faculties: List<Option> = emptyList(),
    val forms: List<Option> = emptyList(),
    val courses: List<Option> = emptyList(),
    val groups: List<Option> = emptyList(),
    val teachers: List<Option> = emptyList(),
    val faculty: Option? = null,
    val form: Option? = null,
    val course: Option? = null,
    val group: Option? = null,
    val teacher: Option? = null,
    val teacherQuery: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class SelectViewModel(
    private val repository: ScheduleRepository,
    private val onSaved: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(SelectUiState())
    val state: StateFlow<SelectUiState> = _state.asStateFlow()

    private var pendingLoads = 0

    init {
        viewModelScope.launch {
            val saved = repository.selectionFlow().first()
            if (saved != null) {
                _state.update {
                    it.copy(
                        mode = saved.mode,
                        faculty = saved.faculty,
                        form = saved.form,
                        course = saved.course,
                        group = saved.group,
                        teacher = saved.teacher,
                    )
                }
                if (saved.mode == Mode.GROUP) {
                    val faculty = saved.faculty
                    val form = saved.form
                    val course = saved.course
                    if (faculty != null) {
                        loadFormsInternal(faculty, form)
                    }
                    if (faculty != null && form != null) {
                        loadCoursesInternal(faculty, form, course)
                    }
                    if (faculty != null && form != null && course != null) {
                        loadGroupsInternal(faculty, form, course, saved.group)
                    }
                }
            }
            loadFacultiesInternal()
            loadTeachersInternal()
        }
    }

    fun setMode(mode: Mode) {
        _state.update { it.copy(mode = mode, error = null) }
    }

    fun loadFaculties() {
        viewModelScope.launch { loadFacultiesInternal() }
    }

    fun loadTeachers() {
        viewModelScope.launch { loadTeachersInternal() }
    }

    fun selectFaculty(option: Option) {
        _state.update {
            it.copy(
                faculty = option,
                form = null,
                course = null,
                group = null,
                forms = emptyList(),
                courses = emptyList(),
                groups = emptyList(),
                error = null,
            )
        }
        viewModelScope.launch { loadFormsInternal(option) }
    }

    fun selectForm(option: Option) {
        val faculty = _state.value.faculty ?: return
        _state.update {
            it.copy(
                form = option,
                course = null,
                group = null,
                courses = emptyList(),
                groups = emptyList(),
                error = null,
            )
        }
        viewModelScope.launch { loadCoursesInternal(faculty, option) }
    }

    fun selectCourse(option: Option) {
        val faculty = _state.value.faculty ?: return
        val form = _state.value.form ?: return
        _state.update {
            it.copy(
                course = option,
                group = null,
                groups = emptyList(),
                error = null,
            )
        }
        viewModelScope.launch { loadGroupsInternal(faculty, form, option) }
    }

    fun selectGroup(option: Option) {
        _state.update { it.copy(group = option, error = null) }
    }

    fun selectTeacher(option: Option) {
        _state.update { it.copy(teacher = option, error = null) }
        save()
    }

    fun setTeacherQuery(query: String) {
        _state.update { it.copy(teacherQuery = query) }
    }

    fun save() {
        val current = _state.value
        val selection = when (current.mode) {
            Mode.GROUP -> {
                val faculty = current.faculty ?: return
                val form = current.form ?: return
                val course = current.course ?: return
                val group = current.group ?: return
                ScheduleSelection(
                    mode = Mode.GROUP,
                    faculty = faculty,
                    form = form,
                    course = course,
                    group = group,
                )
            }

            Mode.TEACHER -> {
                val teacher = current.teacher ?: return
                ScheduleSelection(mode = Mode.TEACHER, teacher = teacher)
            }
        }

        viewModelScope.launch {
            beginLoad()
            try {
                repository.saveSelection(selection)
                onSaved()
            } catch (error: Throwable) {
                handle(error)
            } finally {
                endLoad()
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun loadFacultiesInternal() {
        loadOptions({ repository.loadFaculties() }) { options ->
            _state.update {
                it.copy(
                    faculties = options,
                    faculty = it.faculty?.takeIf { selected ->
                        options.any { option -> option.value == selected.value }
                    },
                    error = null,
                )
            }
        }
    }

    private suspend fun loadTeachersInternal() {
        loadOptions({ repository.loadTeachers() }) { options ->
            _state.update {
                it.copy(
                    teachers = options,
                    teacher = it.teacher?.takeIf { selected ->
                        options.any { option -> option.value == selected.value }
                    },
                    error = null,
                )
            }
        }
    }

    private suspend fun loadFormsInternal(faculty: Option, preferred: Option? = null) {
        loadOptions({ repository.loadForms(faculty) }) { options ->
            _state.update {
                it.copy(
                    forms = options,
                    form = preferred?.takeIf { selected ->
                        options.any { option -> option.value == selected.value }
                    },
                    error = null,
                )
            }
        }
    }

    private suspend fun loadCoursesInternal(
        faculty: Option,
        form: Option,
        preferred: Option? = null,
    ) {
        loadOptions({ repository.loadCourses(faculty, form) }) { options ->
            _state.update {
                it.copy(
                    courses = options,
                    course = preferred?.takeIf { selected ->
                        options.any { option -> option.value == selected.value }
                    },
                    error = null,
                )
            }
        }
    }

    private suspend fun loadGroupsInternal(
        faculty: Option,
        form: Option,
        course: Option,
        preferred: Option? = null,
    ) {
        loadOptions({ repository.loadGroups(faculty, form, course) }) { options ->
            _state.update {
                it.copy(
                    groups = options,
                    group = preferred?.takeIf { selected ->
                        options.any { option -> option.value == selected.value }
                    },
                    error = null,
                )
            }
        }
    }

    private suspend fun loadOptions(
        block: suspend () -> List<Option>,
        apply: (List<Option>) -> Unit,
    ) {
        beginLoad()
        try {
            apply(block())
        } catch (error: Throwable) {
            handle(error)
        } finally {
            endLoad()
        }
    }

    private fun handle(error: Throwable) {
        val message = when (error) {
            is IseuException.CatalogStale -> "Сохранённая группа больше не доступна. Выберите заново."
            is IseuException -> error.message.orEmpty().ifEmpty { "Не удалось загрузить данные" }
            else -> "Не удалось загрузить данные"
        }
        _state.update {
            if (error is IseuException.CatalogStale) {
                it.copy(
                    error = message,
                    form = null,
                    course = null,
                    group = null,
                    forms = emptyList(),
                    courses = emptyList(),
                    groups = emptyList(),
                )
            } else {
                it.copy(error = message)
            }
        }
    }

    private fun beginLoad() {
        pendingLoads++
        _state.update { it.copy(loading = true) }
    }

    private fun endLoad() {
        pendingLoads = (pendingLoads - 1).coerceAtLeast(0)
        if (pendingLoads == 0) {
            _state.update { it.copy(loading = false) }
        }
    }
}
