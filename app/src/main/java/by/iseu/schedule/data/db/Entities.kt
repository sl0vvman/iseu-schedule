package by.iseu.schedule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "week_cache")
data class WeekCacheEntity(
    @PrimaryKey val cacheKey: String,
    val mode: String,
    val targetId: String,
    val targetLabel: String,
    val weekMonday: String,
    val lessonsJson: String,
    val siteMessage: String?,
    val fetchedAtEpochMs: Long,
)

@Entity(tableName = "catalog_cache")
data class CatalogEntry(
    @PrimaryKey val key: String,
    val json: String,
    val fetchedAtEpochMs: Long,
)
