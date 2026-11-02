package by.iseu.schedule.data.db

import by.iseu.schedule.data.model.Lesson
import by.iseu.schedule.data.model.LessonType
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class LessonDto(
    val dayOfWeek: String,
    val date: String,
    val start: String,
    val end: String,
    val subgroup: Int? = null,
    val discipline: String,
    val type: String,
    val staff: String? = null,
    val contingent: String? = null,
    val topic: String? = null,
    val audience: String? = null,
)

fun Lesson.toDto(): LessonDto = LessonDto(
    dayOfWeek = dayOfWeek,
    date = date.toString(),
    start = start.toString(),
    end = end.toString(),
    subgroup = subgroup,
    discipline = discipline,
    type = type.name,
    staff = staff,
    contingent = contingent,
    topic = topic,
    audience = audience,
)

fun LessonDto.toLesson(): Lesson = Lesson(
    dayOfWeek = dayOfWeek,
    date = LocalDate.parse(date),
    start = LocalTime.parse(start),
    end = LocalTime.parse(end),
    subgroup = subgroup,
    discipline = discipline,
    type = LessonType.valueOf(type),
    staff = staff,
    contingent = contingent,
    topic = topic,
    audience = audience,
)
