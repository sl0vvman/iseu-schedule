package by.iseu.schedule.data.parser

import by.iseu.schedule.data.model.Option
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeekSelectorTest {

    private val weeks = listOf(
        Option("02.11.2026 0:00:00", "02.11.2026"),
        Option("09.11.2026 0:00:00", "09.11.2026"),
    )

    @Test
    fun `monday of date`() {
        assertEquals(LocalDate.of(2026, 11, 2), WeekSelector.mondayOf(LocalDate.of(2026, 11, 2)))
        assertEquals(LocalDate.of(2026, 11, 2), WeekSelector.mondayOf(LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun `selects exact week`() {
        assertEquals(weeks[0], WeekSelector.selectWeek(weeks, LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `selects nearest week`() {
        assertEquals(weeks[1], WeekSelector.selectWeek(weeks, LocalDate.of(2026, 11, 8)))
        assertEquals(weeks[0], WeekSelector.selectWeek(weeks, LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun `empty list returns null`() {
        assertNull(WeekSelector.selectWeek(emptyList(), LocalDate.of(2026, 11, 2)))
    }
}
