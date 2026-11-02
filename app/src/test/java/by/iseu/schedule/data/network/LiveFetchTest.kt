package by.iseu.schedule.data.network

import by.iseu.schedule.data.model.Option
import by.iseu.schedule.data.parser.ScheduleParser
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LiveFetchTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jar = MemoryCookieJar()

    @Test
    fun `live group and teacher fetch`() {
        assumeTrue(System.getenv("ISEU_LIVE") == "1")

        val group = AspNetSession(client, GROUP_URL, jar)
        val faculty = group.realOptions("ddlFac").first()
        group.postback("ddlFac", mapOf("ddlFac" to faculty.value))
        val form = group.realOptions("ddlDep").first()
        group.postback("ddlDep", mapOf("ddlDep" to form.value))
        val course = group.realOptions("ddlCourse").first()
        group.postback("ddlCourse", mapOf("ddlCourse" to course.value))
        val groupOption = group.realOptions("ddlGroup").first()
        group.postback("ddlGroup", mapOf("ddlGroup" to groupOption.value))
        val weeks = group.options("ddlWeek")
        assertTrue(weeks.isNotEmpty())
        group.postback("ddlWeek", mapOf("ddlWeek" to weeks.first().value))
        val groupHtml = group.submit(mapOf("ShowTT" to "Показать"))
        ScheduleParser.parseGroupTable(groupHtml)
        ScheduleParser.parseMessage(groupHtml)

        val teacher = AspNetSession(client, TEACHER_URL, jar)
        val teacherOption = teacher.realOptions("DropDownList1").first()
        teacher.postback("DropDownList1", mapOf("DropDownList1" to teacherOption.value))
        val teacherWeeks = teacher.options("ddlWeek")
        assertTrue(teacherWeeks.isNotEmpty())
        teacher.postback("ddlWeek", mapOf("ddlWeek" to teacherWeeks.first().value))
        val teacherHtml = teacher.submit(
            mapOf("Show" to "Показать", "cbShowDraftForTeacher" to "on"),
        )
        ScheduleParser.parseTeacherTable(teacherHtml)
    }

    private fun AspNetSession.realOptions(name: String): List<Option> =
        options(name).filter { it.value != "0" && it.value.isNotBlank() }

    private companion object {
        const val GROUP_URL = "http://rsp.iseu.by/Raspisanie/TimeTable/umu.aspx"
        const val TEACHER_URL = "http://rsp.iseu.by/Raspisanie/TimeTable/umuteachers.aspx"
    }
}
