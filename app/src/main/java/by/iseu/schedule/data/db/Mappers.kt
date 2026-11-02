package by.iseu.schedule.data.db

import by.iseu.schedule.data.model.Mode
import by.iseu.schedule.data.model.WeekSchedule
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

fun WeekSchedule.toEntity(cacheKey: String, json: Json): WeekCacheEntity = WeekCacheEntity(
    cacheKey = cacheKey,
    mode = mode.name,
    targetId = targetId,
    targetLabel = targetLabel,
    weekMonday = weekMonday.toString(),
    lessonsJson = json.encodeToString(lessons.map { it.toDto() }),
    siteMessage = siteMessage,
    fetchedAtEpochMs = fetchedAt.toEpochMilli(),
)

fun WeekCacheEntity.toWeekSchedule(): WeekSchedule {
    val lessons = runCatching {
        Json { ignoreUnknownKeys = true }.decodeFromString<List<LessonDto>>(lessonsJson)
    }.getOrDefault(emptyList()).map { it.toLesson() }

    return WeekSchedule(
        weekMonday = LocalDate.parse(weekMonday),
        mode = Mode.valueOf(mode),
        targetId = targetId,
        targetLabel = targetLabel,
        lessons = lessons,
        siteMessage = siteMessage,
        fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMs),
    )
}
