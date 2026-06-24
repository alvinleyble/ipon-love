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

    suspend fun getCategory(id: String): Category?

    suspend fun upsertCategory(category: Category)

    suspend fun setArchived(id: String, archived: Boolean)

    /** Soft delete — sets `is_deleted = true`; never a hard delete (ADR-0010). */
    suspend fun deleteCategory(id: String)
}
