package by.iseu.schedule.data.parser

import by.iseu.schedule.data.model.LessonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TeacherScheduleParserTest {

    private val html = fixture("teacher_week.html")

    @Test
    fun `parses teacher lesson with contingent and audience`() {
        val lessons = ScheduleParser.parseTeacherTable(html)
        assertTrue(lessons.isNotEmpty())
        val first = lessons.first()
        assertEquals(LocalDate.of(2026, 11, 2), first.date)
        assertTrue(first.contingent!!.contains("ФМОС"))
        assertEquals("Физика", first.discipline)
        assertEquals(LessonType.LECTURE, first.type)
        assertTrue(first.audience!!.contains("ауд.103"))
        assertNull(first.staff)
    }

    @Test
    fun `inherits teacher date from row spanned`() {
        val lessons = ScheduleParser.parseTeacherTable(html)
        assertEquals(LocalDate.of(2026, 11, 2), lessons[1].date)
    }

    @Test
    fun `extracts subgroup marker from topic column`() {
        val lessons = ScheduleParser.parseTeacherTable(html)
        val lab = lessons.first { it.type == LessonType.LAB }
        assertEquals(1, lab.subgroup)
        assertNull(lab.topic)
    }
}
