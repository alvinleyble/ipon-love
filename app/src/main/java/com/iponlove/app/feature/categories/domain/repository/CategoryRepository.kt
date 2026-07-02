package com.iponlove.app.feature.categories.domain.repository

import com.iponlove.app.feature.categories.domain.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Categories source of truth (Room-backed). Like accounts, all writes funnel through
 * here so `updated_at` stamping (ADR-0001), `pending_sync` (ADR-0002) and soft delete
 * (ADR-0010) are applied in one place.
 */
interface CategoryRepository {

    /** Active (non-deleted) categories, ordered by position; archived included only if asked. */
    fun observeCategories(includeArchived: Boolean = false): Flow<List<Category>>

    /**
     * Every member's non-deleted categories (both owners) — used only to resolve category
     * names for the combined couple view (ADR-0011).
     */
    fun observeAllCategories(): Flow<List<Category>>

    suspend fun getCategory(id: String): Category?

    /** Count of this user's personal (non-shared) categories — the onboarding gate (ADR-0024). */
    suspend fun countOwnedCategories(): Int

    suspend fun upsertCategory(category: Category)

    /**
     * Persist a manual drag-handle ordering from Manage (item 9b) — [orderedIds] top-to-bottom.
     * Writes `position = index` for each row whose position actually changed.
     */
    suspend fun reorderCategories(orderedIds: List<String>)

    suspend fun setArchived(id: String, archived: Boolean)

    /** Soft delete — sets `is_deleted = true`; never a hard delete (ADR-0010). */
    suspend fun deleteCategory(id: String)

    /** Make a personal category couple-owned (shared) under [coupleId] (ADR-0018). */
    suspend fun shareCategory(id: String, coupleId: String)

    /** Revert a shared category to its creator's personal category (revert-to-creator, ADR-0018). */
    suspend fun unshareCategory(id: String)

    /**
     * On unpair (ADR-0008): revert couple-owned categories this user created back to personal,
     * delete the partner's couple-owned categories and replicated partner personal categories
     * (ADR-0018).
     */
    suspend fun purgePartnerData()
}
