package by.iseu.schedule.data.parser

import by.iseu.schedule.data.model.LessonType
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TextParsing {
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val TIME = Regex("""(\d{1,2}):(\d{2})\s*[-–]\s*(\d{1,2}):(\d{2})""")
    private val SUBGROUP_BRACKET = Regex("""\[\s*(\d)\s*п\s*/\s*гр\s*\]""", RegexOption.IGNORE_CASE)
    private val SUBGROUP_PLAIN = Regex("""(?:^|\D)(\d)\s*п\s*/\s*гр""", RegexOption.IGNORE_CASE)
    private val SUBGROUP_MARKERS = Regex(
        """\[\s*\d\s*п\s*/\s*гр\s*\]|\d\s*п\s*/\s*гр""",
        RegexOption.IGNORE_CASE,
    )
    private val WHITESPACE = Regex("""\s+""")

    private val TYPE_PREFIXES = listOf(
        "практ. зан." to LessonType.PRACTICE,
        "практическое занятие" to LessonType.PRACTICE,
        "лабораторная работа" to LessonType.LAB,
        "лаб." to LessonType.LAB,
        "лек." to LessonType.LECTURE,
        "сем." to LessonType.OTHER,
    )

    fun parseDate(raw: String): LocalDate? = try {
        LocalDate.parse(raw.trim(), DATE)
    } catch (e: DateTimeParseException) {
        null
    }

    fun parseDayAndDate(raw: String): Pair<String, LocalDate>? {
        val text = raw.replace('\u00A0', ' ').replace(WHITESPACE, " ").trim()
        val parts = text.split(' ')
        if (parts.size < 2) return null
        val date = parseDate(parts.last()) ?: return null
        return parts.dropLast(1).joinToString(" ") to date
    }

    fun parseTimeRange(raw: String): Pair<LocalTime, LocalTime>? {
        val match = TIME.find(raw) ?: return null
        val (hour1, minute1, hour2, minute2) = match.destructured
        return try {
            LocalTime.of(hour1.toInt(), minute1.toInt()) to LocalTime.of(hour2.toInt(), minute2.toInt())
        } catch (e: DateTimeException) {
            null
        }
    }

    fun parseDiscipline(raw: String): Pair<String, LessonType> {
        var text = stripSubgroupMarkers(raw)
        var type = LessonType.OTHER
        for ((prefix, lessonType) in TYPE_PREFIXES) {
            if (text.startsWith(prefix, ignoreCase = true)) {
                text = text.substring(prefix.length).trim()
                type = lessonType
                break
            }
        }
        text = text.trim('"', '«', '»', ' ', ',')
        return text to type
    }

    fun stripSubgroupMarkers(raw: String): String =
        raw.replace('\u00A0', ' ')
            .replace(SUBGROUP_MARKERS, "")
            .replace(WHITESPACE, " ")
            .trim()

    fun parseSubgroup(raw: String?): Int? {
        if (raw == null) return null
        val text = raw.replace('\u00A0', ' ').trim()
        if (text.isEmpty()) return null
        SUBGROUP_BRACKET.find(text)?.let { return it.groupValues[1].toIntOrNull() }
        if (text.length == 1 && text[0].isDigit()) return text.toInt()
        SUBGROUP_PLAIN.find(text)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    fun cleanOrNull(raw: String?): String? {
        if (raw == null) return null
        val text = raw.replace('\u00A0', ' ').replace(WHITESPACE, " ").trim()
        if (text.isEmpty()) return null
        if (text.replace(WHITESPACE, "").lowercase() == "ауд.,") return null
        if (text == ",") return null
        return text
    }
}
