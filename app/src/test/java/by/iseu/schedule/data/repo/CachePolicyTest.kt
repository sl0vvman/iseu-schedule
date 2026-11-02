package by.iseu.schedule.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CachePolicyTest {

    private val now = Instant.parse("2026-11-02T10:00:00Z")

    @Test
    fun `fresh week cache is not refreshed`() {
        assertFalse(CachePolicy.shouldRefresh(now.minusSeconds(29 * 60), now))
    }

    @Test
    fun `stale week cache is refreshed`() {
        assertTrue(CachePolicy.shouldRefresh(now.minusSeconds(31 * 60), now))
    }

    @Test
    fun `missing cache is refreshed`() {
        assertTrue(CachePolicy.shouldRefresh(null, now))
    }

    @Test
    fun `catalog uses its own ttl`() {
        assertFalse(
            CachePolicy.shouldRefresh(
                now.minus(Duration.ofHours(11)),
                now,
                CachePolicy.catalogTtl,
            ),
        )
        assertTrue(
            CachePolicy.shouldRefresh(
                now.minus(Duration.ofHours(13)),
                now,
                CachePolicy.catalogTtl,
            ),
        )
    }
}
