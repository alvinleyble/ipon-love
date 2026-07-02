package com.iponlove.app.feature.categories.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    /**
     * The current user's own categories plus any couple-owned shared categories (ADR-0018),
     * which appear in both partners' pickers. `coupleId IS NOT NULL` admits shared ones;
     * replicated partner *personal* categories (ADR-0004) stay out of the individual view.
     */
    @Query(
        """
        SELECT * FROM categories
        WHERE (userId = :userId OR coupleId IS NOT NULL)
          AND isDeleted = 0 AND (:includeArchived = 1 OR isArchived = 0)
        ORDER BY position ASC, createdAt ASC
        """,
    )
    fun observeCategories(userId: String, includeArchived: Boolean): Flow<List<CategoryEntity>>

    /**
     * Every member's non-deleted categories (both owners). Used only to resolve category
     * names for the combined view (ADR-0011), where a partner transaction's category must
     * still render by name; archived included so historical rows keep their label.
     */
    @Query("SELECT * FROM categories WHERE isDeleted = 0")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    /** Personal (non-shared) row count for the new-user onboarding gate (ADR-0024). */
    @Query("SELECT COUNT(*) FROM categories WHERE userId = :userId AND isDeleted = 0")
    suspend fun countOwned(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    /** Hard-delete one row — used to purge a redacted partner row (ADR-0005). */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Hard-delete replicated partner *personal* rows on unpair (ADR-0008). Couple-owned rows
     * (userId IS NULL) are handled by the revert/delete pair below.
     */
    @Query("DELETE FROM categories WHERE userId IS NOT NULL AND userId <> :userId")
    suspend fun deleteNotOwnedBy(userId: String)

    /** Revert-to-creator for the unpair purge (ADR-0018): a couple-owned category this user
     *  created becomes their personal category. Mirrors the server-side revert in unpair(). */
    @Query(
        """
        UPDATE categories
        SET userId = createdBy, coupleId = NULL, pendingSync = 1, updatedAt = :updatedAt
        WHERE coupleId IS NOT NULL AND createdBy = :userId
        """,
    )
    suspend fun revertOwnCoupleRowsToCreator(userId: String, updatedAt: Long)

    /** Hard-delete couple-owned categories created by the *other* partner on unpair (ADR-0018). */
    @Query("DELETE FROM categories WHERE coupleId IS NOT NULL AND (createdBy IS NULL OR createdBy <> :userId)")
    suspend fun deleteCoupleRowsNotCreatedBy(userId: String)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM categories WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<CategoryEntity>

    @Query("UPDATE categories SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(categories: List<CategoryEntity>)
}
