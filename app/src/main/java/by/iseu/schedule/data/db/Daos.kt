package by.iseu.schedule.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeekCacheDao {
    @Query("SELECT * FROM week_cache WHERE cacheKey = :key")
    suspend fun get(key: String): WeekCacheEntity?

    @Query("SELECT * FROM week_cache WHERE cacheKey = :key")
    fun observe(key: String): Flow<WeekCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: WeekCacheEntity)

    @Query("DELETE FROM week_cache")
    suspend fun clear()
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_cache WHERE key = :key")
    suspend fun get(key: String): CatalogEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: CatalogEntry)

    @Query("DELETE FROM catalog_cache")
    suspend fun clear()
}
