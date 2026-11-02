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
import org.junit.Assert.assertFalse
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
    fun `opens a teacher schedule and returns to the saved group`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
            teachers = listOf(Option("-446", "Авласович Алексей Михайлович (доц.)"))
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()

        vm.openTeacher("доц. Авласович Алексей Михайлович")
        advanceUntilIdle()

        assertTrue(vm.state.value.isTeacherPreview)
        assertEquals(Mode.TEACHER, vm.state.value.activeSelection?.mode)
        assertEquals(
            "Авласович Алексей Михайлович (доц.)",
            vm.state.value.activeSelection?.targetLabel,
        )
        assertEquals(Mode.GROUP, fake.selection.value?.mode)
        assertEquals(Mode.TEACHER, fake.loadedSelections.last().mode)

        vm.closeTeacherPreview()
        advanceUntilIdle()

        assertFalse(vm.state.value.isTeacherPreview)
        assertEquals(Mode.GROUP, vm.state.value.activeSelection?.mode)
        assertEquals(Mode.GROUP, fake.loadedSelections.last().mode)
        assertEquals(Mode.GROUP, fake.selection.value?.mode)
    }

    @Test
    fun `selection re-emission does not close a teacher preview`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
            teachers = listOf(Option("-446", "Авласович Алексей Михайлович (доц.)"))
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()
        vm.openTeacher("доц. Авласович Алексей Михайлович")
        advanceUntilIdle()
        assertTrue(vm.state.value.isTeacherPreview)

        val saved = fake.groupSelection()
        fake.selection.value = saved.copy(
            group = saved.group!!.copy(label = "${saved.group.label} "),
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.isTeacherPreview)
    }

    @Test
    fun `unknown teacher in a group lesson produces a notice, not a preview`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository().apply {
            selection.value = groupSelection()
        }
        val vm = ScheduleViewModel(fake) { LocalDate.of(2026, 11, 2) }
        advanceUntilIdle()

        vm.openTeacher("пр. Неизвестный Иван Иванович")
        advanceUntilIdle()

        assertFalse(vm.state.value.isTeacherPreview)
        assertNotNull(vm.state.value.notice)
        assertEquals(Mode.GROUP, vm.state.value.activeSelection?.mode)
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
