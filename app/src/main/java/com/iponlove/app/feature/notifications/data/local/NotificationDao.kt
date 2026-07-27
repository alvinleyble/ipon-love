package com.iponlove.app.feature.notifications.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeInbox(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isDeleted = 0 AND isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT * FROM notifications WHERE isDeleted = 0 AND isRead = 0")
    suspend fun unread(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isDeleted = 0")
    suspend fun active(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun getById(id: String): NotificationEntity?

    /**
     * Every id starting with [prefix], **including read, dismissed, and soft-deleted rows** —
     * this is the dedup record (ADR-0053 decision 3), so a dismissed alert must read as
     * "already raised" and never fire again.
     */
    @Query("SELECT id FROM notifications WHERE id LIKE :prefix || '%'")
    suspend fun idsWithPrefix(prefix: String): List<String>

    /**
     * Create-if-absent, atomically: IGNORE makes a conflicting insert a no-op and returns -1,
     * so a re-detected event can never clobber the existing row's read/dismissed state.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun upsertAll(rows: List<NotificationEntity>)

    // ---- 60-day retention (ADR-0053 decision 4) ----
    // Genuine deletion, not a tombstone — a sanctioned narrow exception to ADR-0010, safe
    // because "older than the cutoff" is computed identically on every client.

    @Query("SELECT * FROM notifications WHERE createdAt < :cutoff")
    suspend fun expiredBefore(cutoff: Instant): List<NotificationEntity>

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun hardDelete(ids: List<String>)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM notifications WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<NotificationEntity>

    @Query("UPDATE notifications SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(rows: List<NotificationEntity>)
}
