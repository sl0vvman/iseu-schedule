package by.iseu.schedule.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionParserTest {

    @Test
    fun `parses teacher options`() {
        val teachers = OptionParser.parseOptions(fixture("teachers_page.html"), "DropDownList1")
        assertEquals(241, teachers.size)
        assertEquals("-414", teachers[1].value)
        assertEquals("Авдеева Екатерина Владимировна (пр.)", teachers[1].label)
    }

    @Test
    fun `parses week options`() {
        val weeks = OptionParser.parseOptions(fixture("group_week.html"), "ddlWeek")
        assertTrue(weeks.isNotEmpty())
        assertTrue(weeks.first().value.endsWith(" 0:00:00"))
    }
}
