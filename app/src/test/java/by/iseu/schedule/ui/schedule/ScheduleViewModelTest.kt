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
