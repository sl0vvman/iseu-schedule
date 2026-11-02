package by.iseu.schedule.ui.schedule

import by.iseu.schedule.FakeScheduleRepository
import by.iseu.schedule.data.model.IseuException
import by.iseu.schedule.data.model.Lesson
import by.iseu.schedule.data.model.LessonType
import by.iseu.schedule.data.model.Mode
import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.model.ScheduleSelection
import by.iseu.schedule.data.model.WeekSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `navigates weeks and clamps at ends`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 11, 2), vm.state.value.weekMonday)
        assertEquals(2, vm.state.value.weeks.size)

        vm.nextWeek()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 9), vm.state.value.weekMonday)

        vm.nextWeek()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 9), vm.state.value.weekMonday)

        vm.previousWeek()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 2), vm.state.value.weekMonday)
    }

    @Test
    fun `reloads schedule when the saved selection changes`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()
        assertEquals(Mode.GROUP, vm.state.value.selection?.mode)
        assertEquals("А61ИТ1", vm.state.value.selection?.targetLabel)
        val loadsBefore = fake.loadWeekCalls

        fake.selection.value = ScheduleSelection(
            mode = Mode.TEACHER,
            teacher = Option("-414", "Авдеева Екатерина Владимировна (пр.)"),
        )
        advanceUntilIdle()

        assertEquals(Mode.TEACHER, vm.state.value.selection?.mode)
        assertEquals("Авдеева Екатерина Владимировна (пр.)", vm.state.value.selection?.targetLabel)
        assertTrue(fake.loadWeekCalls > loadsBefore)
    }

    @Test
    fun `cached empty week is an empty state, not an error`() {
        val cachedEmpty = ScheduleUiState(
            lessons = emptyList(),
            error = "Нет сети",
            isFromCache = true,
        )
        val noCache = ScheduleUiState(lessons = emptyList(), error = "Нет сети")

        assertEquals(ScheduleContent.EMPTY, cachedEmpty.content())
        assertEquals(ScheduleContent.ERROR, noCache.content())
    }

    @Test
    fun `clears the saved target when the catalog no longer has it`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
            loadWeekError = IseuException.CatalogStale()
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()

        assertNull(fake.selection.value)
        assertEquals(1, fake.clearSelectionCalls)
        assertEquals(1, fake.clearCatalogCacheCalls)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `shows cached lessons when network fails`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
            cached = WeekSchedule(
                weekMonday = LocalDate.of(2026, 11, 2),
                mode = Mode.GROUP,
                targetId = "369",
                targetLabel = "А61ИТ1",
                lessons = listOf(sampleLesson()),
                siteMessage = null,
                fetchedAt = Instant.parse("2026-11-02T08:00:00Z"),
            )
            loadWeekError = IseuException.Network(IOException("offline"))
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertEquals(1, vm.state.value.lessons.size)
        assertTrue(vm.state.value.isFromCache)
    }

    @Test
    fun `opens on today when today is in the current week`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 4) }
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 11, 4), vm.state.value.selectedDay)
    }

    @Test
    fun `selected day follows week navigation and returns to today`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 4) }
        advanceUntilIdle()

        vm.selectDay(LocalDate.of(2026, 11, 5))
        assertEquals(LocalDate.of(2026, 11, 5), vm.state.value.selectedDay)

        vm.nextWeek()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 9), vm.state.value.weekMonday)
        assertEquals(LocalDate.of(2026, 11, 9), vm.state.value.selectedDay)

        vm.currentWeek()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 2), vm.state.value.weekMonday)
        assertEquals(LocalDate.of(2026, 11, 4), vm.state.value.selectedDay)
    }

    @Test
    fun `teacher tab loads the catalog without touching the saved group`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
            teachers = listOf(
                Option("-446", "Авласович Алексей Михайлович (доц.)"),
                Option("-414", "Авдеева Екатерина Владимировна (пр.)"),
            )
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()
        val groupLoads = fake.loadWeekCalls

        vm.selectTab(ScheduleTab.TEACHERS)
        advanceUntilIdle()

        assertEquals(ScheduleTab.TEACHERS, vm.state.value.tab)
        assertEquals(2, vm.state.value.teachers.size)
        assertNull(vm.state.value.activeSelection)
        assertEquals(groupLoads, fake.loadWeekCalls)
        assertEquals(Mode.GROUP, fake.selection.value?.mode)
    }

    @Test
    fun `selecting a teacher shows their schedule and keeps the saved group`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
            teachers = listOf(Option("-446", "Авласович Алексей Михайлович (доц.)"))
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()
        vm.selectTab(ScheduleTab.TEACHERS)
        advanceUntilIdle()

        vm.selectTeacher(fake.teachers.first())
        advanceUntilIdle()

        assertEquals(ScheduleTab.TEACHERS, vm.state.value.tab)
        assertEquals(Mode.TEACHER, vm.state.value.activeSelection?.mode)
        assertEquals(
            "Авласович Алексей Михайлович (доц.)",
            vm.state.value.activeSelection?.targetLabel,
        )
        assertEquals(Mode.GROUP, fake.selection.value?.mode)
        assertEquals(Mode.TEACHER, fake.loadedSelections.last().mode)
        assertEquals(LocalDate.of(2026, 11, 2), vm.state.value.weekMonday)
    }

    @Test
    fun `closing a teacher returns to the teacher list`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
            teachers = listOf(Option("-446", "Авласович Алексей Михайлович (доц.)"))
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()
        vm.selectTab(ScheduleTab.TEACHERS)
        advanceUntilIdle()
        vm.selectTeacher(fake.teachers.first())
        advanceUntilIdle()

        vm.closeTeacher()
        advanceUntilIdle()

        assertEquals(ScheduleTab.TEACHERS, vm.state.value.tab)
        assertNull(vm.state.value.activeSelection)
        assertTrue(vm.state.value.lessons.isEmpty())
        assertEquals(Mode.GROUP, fake.selection.value?.mode)
    }

    @Test
    fun `switching back to the schedule tab reloads the saved group`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
            teachers = listOf(Option("-446", "Авласович Алексей Михайлович (доц.)"))
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()
        vm.selectTab(ScheduleTab.TEACHERS)
        advanceUntilIdle()
        vm.selectTeacher(fake.teachers.first())
        advanceUntilIdle()

        vm.selectTab(ScheduleTab.SCHEDULE)
        advanceUntilIdle()

        assertEquals(ScheduleTab.SCHEDULE, vm.state.value.tab)
        assertEquals(Mode.GROUP, vm.state.value.activeSelection?.mode)
        assertEquals(Mode.GROUP, fake.loadedSelections.last().mode)
    }

    @Test
    fun `teacher catalog failure shows an error and retry recovers`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
            loadTeachersError = IseuException.Network(IOException("offline"))
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()

        vm.selectTab(ScheduleTab.TEACHERS)
        advanceUntilIdle()

        assertNotNull(vm.state.value.teachersError)
        assertTrue(vm.state.value.teachers.isEmpty())

        fake.loadTeachersError = null
        fake.teachers = listOf(Option("-446", "Авласович Алексей Михайлович (доц.)"))
        vm.reloadTeachers()
        advanceUntilIdle()

        assertNull(vm.state.value.teachersError)
        assertEquals(1, vm.state.value.teachers.size)
    }

    @Test
    fun `saved target change does not replace an open teacher schedule`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
            teachers = listOf(Option("-446", "Авласович Алексей Михайлович (доц.)"))
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()
        vm.selectTab(ScheduleTab.TEACHERS)
        advanceUntilIdle()
        vm.selectTeacher(fake.teachers.first())
        advanceUntilIdle()
        val loads = fake.loadWeekCalls

        fake.selection.value = ScheduleSelection(
            mode = Mode.GROUP,
            faculty = fake.faculties.first(),
            form = fake.forms.first(),
            course = fake.courses.first(),
            group = Option("370", "А62ИТ1"),
        )
        advanceUntilIdle()

        assertEquals(ScheduleTab.TEACHERS, vm.state.value.tab)
        assertEquals(Mode.TEACHER, vm.state.value.activeSelection?.mode)
        assertEquals(loads, fake.loadWeekCalls)
        assertEquals("А62ИТ1", vm.state.value.selection?.targetLabel)
    }

    private fun sampleLesson() = Lesson(
        dayOfWeek = "Понедельник",
        date = LocalDate.of(2026, 11, 2),
        start = LocalTime.of(8, 30),
        end = LocalTime.of(9, 50),
        subgroup = null,
        discipline = "Физика",
        type = LessonType.LECTURE,
        staff = "Луцевич",
        contingent = null,
        topic = null,
        audience = "ауд. 103, К2",
    )
}
