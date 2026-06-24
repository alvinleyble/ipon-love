package com.iponlove.app.feature.categories.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    /** The current user's own categories — filtered to [userId] so replicated partner
     *  categories (ADR-0004) never leak into the individual view. */
    @Query(
        """
        SELECT * FROM categories
        WHERE userId = :userId AND isDeleted = 0 AND (:includeArchived = 1 OR isArchived = 0)
        ORDER BY position ASC, createdAt ASC
        """,
    )
    fun observeCategories(userId: String, includeArchived: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    /** Hard-delete one row — used to purge a redacted partner row (ADR-0005). */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Hard-delete every replicated partner row on unpair (ADR-0008). */
    @Query("DELETE FROM categories WHERE userId <> :userId")
    suspend fun deleteNotOwnedBy(userId: String)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM categories WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<CategoryEntity>

    @Query("UPDATE categories SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(categories: List<CategoryEntity>)
}
