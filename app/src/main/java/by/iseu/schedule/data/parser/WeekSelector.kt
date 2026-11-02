package by.iseu.schedule.data.parser

import by.iseu.schedule.data.model.Option
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object WeekSelector {
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    fun parseOptionDate(label: String): LocalDate? {
        val candidate = label.trim().substringBefore(' ')
        return try {
            LocalDate.parse(candidate, DATE)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun selectWeek(weeks: List<Option>, targetMonday: LocalDate): Option? {
        if (weeks.isEmpty()) return null
        val parsed = weeks.mapNotNull { week -> parseOptionDate(week.label)?.let { it to week } }
        if (parsed.isEmpty()) return weeks.first()
        return parsed.firstOrNull { it.first == targetMonday }?.second
            ?: parsed.minByOrNull { abs(ChronoUnit.DAYS.between(it.first, targetMonday)) }?.second
    }
}
