package by.iseu.schedule.data.parser

import by.iseu.schedule.data.model.LessonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class GroupScheduleParserTest {

    private val html = fixture("group_week.html")

    @Test
    fun `parses first group lesson`() {
        val lessons = ScheduleParser.parseGroupTable(html)
        assertTrue(lessons.isNotEmpty())
        val first = lessons.first()
        assertEquals("Понедельник", first.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 2), first.date)
        assertEquals(LocalTime.of(8, 30), first.start)
        assertEquals(LocalTime.of(9, 50), first.end)
        assertEquals("Физика", first.discipline)
        assertEquals(LessonType.LECTURE, first.type)
        assertTrue(first.staff!!.contains("Луцевич"))
        assertEquals("ауд. 103, К2", first.audience)
        assertNull(first.subgroup)
    }

    @Test
    fun `inherits date from row spanned`() {
        val lessons = ScheduleParser.parseGroupTable(html)
        assertEquals(LocalDate.of(2026, 11, 2), lessons[1].date)
        assertTrue(lessons.count { it.date == LocalDate.of(2026, 11, 2) } > 1)
    }

    @Test
    fun `empty staff and audience become null`() {
        val lessons = ScheduleParser.parseGroupTable(html)
        val pe = lessons.first { it.discipline == "Физическая культура" }
        assertNull(pe.staff)
        assertNull(pe.audience)
    }

    @Test
    fun `parses subgroup and strips marker once`() {
        val synthetic = """
            <table id="TT">
              <tr class="row row-spanned">
                <td class="cell-date"><span class="day">Понедельник</span> <span class="date">02.11.2026</span></td>
                <td class="cell-time">08:30-09:50</td>
                <td class="cell-subgroup">2</td>
                <td class="cell-discipline">практ. зан. Физика [1п/гр]</td>
                <td class="cell-staff">Иванов</td>
                <td class="cell-auditory">ауд. 1</td>
              </tr>
            </table>
        """.trimIndent()
        val lesson = ScheduleParser.parseGroupTable(synthetic).single()
        assertEquals(2, lesson.subgroup)
        assertEquals("Физика", lesson.discipline)
    }

    @Test
    fun `parses rows without class attribute`() {
        val synthetic = """
            <table id="TT">
              <tr class="row row-spanned">
                <td class="cell-date"><span class="day">Понедельник</span> <span class="date">02.11.2026</span></td>
                <td class="cell-time">08:30-09:50</td>
                <td class="cell-subgroup"></td>
                <td class="cell-discipline">лек. Физика</td>
                <td class="cell-staff">Иванов</td>
                <td class="cell-auditory">ауд. 1</td>
              </tr>
              <tr>
                <td class="cell-time">10:05-11:25</td>
                <td class="cell-subgroup"></td>
                <td class="cell-discipline">практ. зан. Физика</td>
                <td class="cell-staff">Петров</td>
                <td class="cell-auditory">ауд. 2</td>
              </tr>
            </table>
        """.trimIndent()
        assertEquals(2, ScheduleParser.parseGroupTable(synthetic).size)
    }

    @Test
    fun `empty week is not an error`() {
        val synthetic = """
            <table id="TT">
              <tr class="row row-empty">
                <td class="cell-date"><span class="day">Понедельник</span> <span class="date">02.11.2026</span></td>
                <td></td>
              </tr>
            </table>
        """.trimIndent()
        assertTrue(ScheduleParser.parseGroupTable(synthetic).isEmpty())
    }
}

internal fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
        "fixture not found: $name"
    }.bufferedReader().use { it.readText() }
