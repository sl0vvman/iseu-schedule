package by.iseu.schedule.data.parser

import by.iseu.schedule.data.model.Lesson
import org.jsoup.Jsoup
import java.time.LocalDate

object ScheduleParser {

    fun parseGroupTable(html: String): List<Lesson> {
        val table = Jsoup.parse(html).selectFirst("table#TT") ?: return emptyList()
        val lessons = mutableListOf<Lesson>()
        var dayOfWeek = ""
        var date: LocalDate? = null

        for (row in table.select("tr")) {
            if (row.hasClass("row-header") || row.hasClass("row-separator")) continue
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.isEmpty()) continue

            cells.firstOrNull { it.hasClass("cell-date") }?.let { dateCell ->
                TextParsing.parseDayAndDate(dateCell.text())?.let { (day, parsedDate) ->
                    dayOfWeek = day
                    date = parsedDate
                }
            }

            val timeCell = cells.firstOrNull { it.hasClass("cell-time") } ?: continue
            val time = TextParsing.parseTimeRange(timeCell.text()) ?: continue
            val currentDate = date ?: continue
            val disciplineCell = cells.firstOrNull { it.hasClass("cell-discipline") } ?: continue

            val rawDiscipline = disciplineCell.text()
            val (discipline, type) = TextParsing.parseDiscipline(rawDiscipline)
            if (discipline.isBlank()) continue

            val subgroup = TextParsing.parseSubgroup(cells.firstOrNull { it.hasClass("cell-subgroup") }?.text())
                ?: TextParsing.parseSubgroup(rawDiscipline)

            lessons += Lesson(
                dayOfWeek = dayOfWeek,
                date = currentDate,
                start = time.first,
                end = time.second,
                subgroup = subgroup,
                discipline = discipline,
                type = type,
                staff = TextParsing.cleanOrNull(cells.firstOrNull { it.hasClass("cell-staff") }?.text()),
                contingent = null,
                topic = null,
                audience = TextParsing.cleanOrNull(cells.firstOrNull { it.hasClass("cell-auditory") }?.text()),
            )
        }
        return lessons
    }

    fun parseTeacherTable(html: String): List<Lesson> {
        val table = Jsoup.parse(html).selectFirst("table#TT") ?: return emptyList()
        val lessons = mutableListOf<Lesson>()
        var dayOfWeek = ""
        var date: LocalDate? = null

        for (row in table.select("tr")) {
            if (row.hasClass("row-header") || row.hasClass("row-separator")) continue
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.isEmpty()) continue

            var offset = 0
            cells.firstOrNull { it.hasClass("cell-date") }?.let { dateCell ->
                TextParsing.parseDayAndDate(dateCell.text())?.let { (day, parsedDate) ->
                    dayOfWeek = day
                    date = parsedDate
                }
                offset = cells.indexOf(dateCell) + 1
            }

            if (offset + 4 >= cells.size) continue
            val time = TextParsing.parseTimeRange(cells[offset].text()) ?: continue
            val currentDate = date ?: continue

            val rawDiscipline = cells[offset + 2].text()
            val (discipline, type) = TextParsing.parseDiscipline(rawDiscipline)
            if (discipline.isBlank()) continue

            val rawTopic = cells[offset + 3].text()
            val subgroup = TextParsing.parseSubgroup(rawTopic)
                ?: TextParsing.parseSubgroup(rawDiscipline)
            val topic = TextParsing.cleanOrNull(TextParsing.stripSubgroupMarkers(rawTopic))

            lessons += Lesson(
                dayOfWeek = dayOfWeek,
                date = currentDate,
                start = time.first,
                end = time.second,
                subgroup = subgroup,
                discipline = discipline,
                type = type,
                staff = null,
                contingent = TextParsing.cleanOrNull(cells[offset + 1].text()),
                topic = topic,
                audience = TextParsing.cleanOrNull(cells[offset + 4].text()),
            )
        }
        return lessons
    }

    fun parseMessage(html: String): String? =
        Jsoup.parse(html).selectFirst("[id=lMessage]")?.text()?.trim()?.takeIf { it.isNotEmpty() }
}
