package by.iseu.schedule.ui.select

import by.iseu.schedule.FakeScheduleRepository
import by.iseu.schedule.data.model.IseuException
import by.iseu.schedule.data.model.Option
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

@OptIn(ExperimentalCoroutinesApi::class)
class SelectViewModelTest {

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
    fun `cascades group selection and saves`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository()
        var saved = false
        val vm = SelectViewModel(fake) { saved = true }
        advanceUntilIdle()

        vm.selectFaculty(fake.faculties.first())
        advanceUntilIdle()
        vm.selectForm(fake.forms.first())
        advanceUntilIdle()
        vm.selectCourse(fake.courses.first())
        advanceUntilIdle()
        vm.selectGroup(fake.groups.first())
        vm.save()
        advanceUntilIdle()

        assertEquals(fake.groups.first(), fake.saved?.group)
        assertTrue(saved)
    }

    @Test
    fun `changing faculty resets dependent fields`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository()
        val vm = SelectViewModel(fake) {}
        advanceUntilIdle()

        vm.selectFaculty(fake.faculties[0])
        advanceUntilIdle()
        vm.selectForm(fake.forms.first())
        advanceUntilIdle()
        vm.selectCourse(fake.courses.first())
        advanceUntilIdle()
        vm.selectGroup(fake.groups.first())

        vm.selectFaculty(Option("3", "ФЭМ"))
        advanceUntilIdle()

        assertNull(vm.state.value.form)
        assertNull(vm.state.value.course)
        assertNull(vm.state.value.group)
        assertTrue(vm.state.value.groups.isEmpty())
    }

    @Test
    fun `catalog stale clears selection and shows error`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository()
        fake.loadGroupsError = IseuException.CatalogStale()
        val vm = SelectViewModel(fake) {}
        advanceUntilIdle()

        vm.selectFaculty(fake.faculties.first())
        advanceUntilIdle()
        vm.selectForm(fake.forms.first())
        advanceUntilIdle()
        vm.selectCourse(fake.courses.first())
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.group)
    }

    @Test
    fun `selecting teacher saves immediately`() = runTest(dispatcher) {
        val fake = FakeScheduleRepository()
        var saved = false
        val vm = SelectViewModel(fake) { saved = true }
        advanceUntilIdle()

        vm.setMode(by.iseu.schedule.data.model.Mode.TEACHER)
        vm.selectTeacher(fake.teachers.first())
        advanceUntilIdle()

        assertEquals(fake.teachers.first(), fake.saved?.teacher)
        assertTrue(saved)
    }
}
