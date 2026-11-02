package by.iseu.schedule.data.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class Mode { GROUP, TEACHER }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class LessonType(val label: String) {
    LECTURE("лекция"),
    PRACTICE("практика"),
    LAB("лабораторная"),
    OTHER("занятие"),
}

@Serializable
data class Option(val value: String, val label: String)

data class Lesson(
    val dayOfWeek: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val subgroup: Int?,
    val discipline: String,
    val type: LessonType,
    val staff: String?,
    val contingent: String?,
    val topic: String?,
    val audience: String?,
)

data class ScheduleSelection(
    val mode: Mode,
    val faculty: Option? = null,
    val form: Option? = null,
    val course: Option? = null,
    val group: Option? = null,
    val teacher: Option? = null,
) {
    val targetId: String
        get() = when (mode) {
            Mode.GROUP -> requireNotNull(group) { "group is required" }.value
            Mode.TEACHER -> requireNotNull(teacher) { "teacher is required" }.value
        }

    val targetLabel: String
        get() = when (mode) {
            Mode.GROUP -> requireNotNull(group) { "group is required" }.label
            Mode.TEACHER -> requireNotNull(teacher) { "teacher is required" }.label
        }

    val cacheKey: String
        get() = "${mode.name.lowercase()}:$targetId"
}

data class WeekSchedule(
    val weekMonday: LocalDate,
    val mode: Mode,
    val targetId: String,
    val targetLabel: String,
    val lessons: List<Lesson>,
    val siteMessage: String?,
    val fetchedAt: Instant,
)

data class SchedulePage(
    val weeks: List<Option>,
    val schedule: WeekSchedule,
)

sealed class IseuException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : IseuException("Нет соединения с сайтом", cause)
    class Site(val status: Int) : IseuException("Сайт ответил ошибкой $status")
    class Parse(cause: Throwable) : IseuException("Не удалось разобрать ответ сайта", cause)
    class CatalogStale : IseuException("Сохранённая группа больше не доступна")
}
