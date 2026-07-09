package com.iponlove.app.core.analytics.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalyticsEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AnalyticsEventEntity)

    /** The whole buffer, oldest first — the flush batch. */
    @Query("SELECT * FROM analytics_events ORDER BY createdAt ASC")
    suspend fun all(): List<AnalyticsEventEntity>

    /** Drop the rows that flushed cleanly; leaves any concurrently-logged newer rows. */
    @Query("DELETE FROM analytics_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /**
     * Safety valve so a device that never syncs can't buffer telemetry forever: keep the newest
     * [keep] rows, delete the rest. Uses a SELECT-LIMIT subquery (not `DELETE … LIMIT`, which
     * Android's bundled SQLite isn't compiled for).
     */
    @Query(
        "DELETE FROM analytics_events WHERE id NOT IN " +
            "(SELECT id FROM analytics_events ORDER BY createdAt DESC LIMIT :keep)",
    )
    suspend fun trimToNewest(keep: Int)
}
