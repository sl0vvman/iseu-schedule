package by.iseu.schedule

import by.iseu.schedule.data.model.Mode
import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.model.SchedulePage
import by.iseu.schedule.data.model.ScheduleSelection
import by.iseu.schedule.data.model.ThemeMode
import by.iseu.schedule.data.model.WeekSchedule
import by.iseu.schedule.data.repo.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate

class FakeScheduleRepository : ScheduleRepository {

    val selection = MutableStateFlow<ScheduleSelection?>(null)
    val theme = MutableStateFlow(ThemeMode.SYSTEM)

    var faculties = listOf(Option("2", "ФМОС"), Option("3", "ФЭМ"))
    var forms = listOf(Option("2", "дневная форма"))
    var courses = listOf(Option("1", "1 курс"))
    var groups = listOf(Option("369", "А61ИТ1"))
    var teachers = listOf(Option("-414", "Авдеева Екатерина Владимировна (пр.)"))

    var loadGroupsError: Throwable? = null
    var loadWeekError: Throwable? = null
    var loadWeekCalls = 0

    var weeks: List<Option> = listOf(
        Option("02.11.2026 0:00:00", "02.11.2026"),
        Option("09.11.2026 0:00:00", "09.11.2026"),
    )
    var cached: WeekSchedule? = null
    var weekPage: SchedulePage? = null

    var saved: ScheduleSelection? = null

    override fun selectionFlow(): Flow<ScheduleSelection?> = selection

    override fun themeFlow(): Flow<ThemeMode> = theme

    override suspend fun saveSelection(selection: ScheduleSelection) {
        saved = selection
        this.selection.value = selection
    }

    override suspend fun setTheme(mode: ThemeMode) {
        theme.value = mode
    }

    override suspend fun loadFaculties(force: Boolean): List<Option> = faculties

    override suspend fun loadForms(faculty: Option, force: Boolean): List<Option> = forms

    override suspend fun loadCourses(
        faculty: Option,
        form: Option,
        force: Boolean,
    ): List<Option> = courses

    override suspend fun loadGroups(
        faculty: Option,
        form: Option,
        course: Option,
        force: Boolean,
    ): List<Option> {
        loadGroupsError?.let { throw it }
        return groups
    }

    override suspend fun loadTeachers(force: Boolean): List<Option> = teachers

    override fun cachedWeek(
        selection: ScheduleSelection,
        monday: LocalDate,
    ): Flow<WeekSchedule?> = flowOf(cached)

    override suspend fun cachedWeeks(selection: ScheduleSelection): List<Option> = weeks

    override suspend fun loadWeek(
        selection: ScheduleSelection,
        monday: LocalDate,
        force: Boolean,
    ): SchedulePage {
        loadWeekCalls++
        loadWeekError?.let { throw it }
        return weekPage ?: SchedulePage(
            weeks = weeks,
            schedule = WeekSchedule(
                weekMonday = monday,
                mode = selection.mode,
                targetId = selection.targetId,
                targetLabel = selection.targetLabel,
                lessons = emptyList(),
                siteMessage = null,
                fetchedAt = Instant.parse("2026-11-02T10:00:00Z"),
            ),
        )
    }

    override suspend fun clearCache() = Unit

    fun groupSelection(): ScheduleSelection = ScheduleSelection(
        mode = Mode.GROUP,
        faculty = faculties.first(),
        form = forms.first(),
        course = courses.first(),
        group = groups.first(),
    )
}
