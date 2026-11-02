package by.iseu.schedule.data.repo

import java.time.Duration
import java.time.Instant

object CachePolicy {
    val weekTtl: Duration = Duration.ofMinutes(30)
    val catalogTtl: Duration = Duration.ofHours(12)

    fun shouldRefresh(
        fetchedAt: Instant?,
        now: Instant,
        ttl: Duration = weekTtl,
    ): Boolean {
        if (fetchedAt == null) return true
        return Duration.between(fetchedAt, now) > ttl
    }
}
