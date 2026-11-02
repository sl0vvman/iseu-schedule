package by.iseu.schedule.data.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

enum class Mode { GROUP, TEACHER }

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
