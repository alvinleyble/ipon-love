package com.iponlove.app.feature.couple.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CoupleDao {

    @Upsert
    suspend fun upsertAll(entities: List<CoupleEntity>)

    @Query("SELECT * FROM couples WHERE id = :id")
    suspend fun getById(id: String): CoupleEntity?

    @Query("SELECT * FROM couples WHERE id = :id")
    fun observeById(id: String): Flow<CoupleEntity?>

    // Couples are never locally mutated (RPC-only writes), so the outbox is always empty —
    // these exist only to satisfy the uniform BaseTableSyncer contract.
    @Query("SELECT * FROM couples WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<CoupleEntity>

    @Query("UPDATE couples SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)
}
