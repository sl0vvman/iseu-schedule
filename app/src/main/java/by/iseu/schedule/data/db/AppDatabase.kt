package by.iseu.schedule.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WeekCacheEntity::class, CatalogEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weekCacheDao(): WeekCacheDao
    abstract fun catalogDao(): CatalogDao
}
