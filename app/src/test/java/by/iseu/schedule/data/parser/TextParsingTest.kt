package by.iseu.schedule.data.parser

import by.iseu.schedule.data.model.LessonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TextParsingTest {

    @Test
    fun `parses dates`() {
        assertEquals(LocalDate.of(2026, 11, 2), TextParsing.parseDate("02.11.2026"))
        assertNull(TextParsing.parseDate("нет даты"))
    }

    @Test
    fun `parses day and date`() {
        assertEquals(
            "Понедельник" to LocalDate.of(2026, 11, 2),
            TextParsing.parseDayAndDate("Понедельник 02.11.2026"),
        )
    }

    @Test
    fun `parses time range`() {
        assertEquals(
            LocalTime.of(8, 30) to LocalTime.of(9, 50),
            TextParsing.parseTimeRange("08:30-09:50"),
        )
        assertNull(TextParsing.parseTimeRange("нет пар"))
    }

    @Test
    fun `parses lesson types`() {
        assertEquals("Физика" to LessonType.LECTURE, TextParsing.parseDiscipline("лек. Физика"))
        assertEquals("Физика" to LessonType.PRACTICE, TextParsing.parseDiscipline("практ. зан. Физика"))
        assertEquals(
            "Электричество и магнетизм" to LessonType.LAB,
            TextParsing.parseDiscipline("лаб. \"Электричество и магнетизм\""),
        )
        assertEquals(
            "Физическая культура" to LessonType.OTHER,
            TextParsing.parseDiscipline("Физическая культура"),
        )
    }

    @Test
    fun `strips subgroup marker from discipline`() {
        assertEquals(
            "Физика" to LessonType.PRACTICE,
            TextParsing.parseDiscipline("практ. зан. Физика [1п/гр]"),
        )
        assertEquals(1, TextParsing.parseSubgroup("практ. зан. Физика [1п/гр]"))
    }

    @Test
    fun `parses subgroup markers`() {
        assertEquals(1, TextParsing.parseSubgroup("[1п/гр]"))
        assertEquals(2, TextParsing.parseSubgroup("2"))
        assertEquals(3, TextParsing.parseSubgroup("3 п/гр"))
        assertNull(TextParsing.parseSubgroup(""))
        assertNull(TextParsing.parseSubgroup(null))
    }

    @Test
    fun `cleans empty values`() {
        assertNull(TextParsing.cleanOrNull("ауд. ,"))
        assertNull(TextParsing.cleanOrNull("   "))
        assertNull(TextParsing.cleanOrNull(null))
        assertEquals("ауд. 103, К2", TextParsing.cleanOrNull("  ауд.  103,  К2 "))
    }
}
