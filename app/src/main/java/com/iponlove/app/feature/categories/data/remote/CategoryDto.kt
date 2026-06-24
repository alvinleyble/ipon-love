package com.iponlove.app.feature.categories.data.remote

import com.iponlove.app.feature.categories.domain.model.CategoryType
import java.time.Instant

/**
 * Wire shape of a `categories` row for Supabase. Omits `pendingSync` (local-only,
 * ADR-0002). Serialization annotations arrive with the Supabase slice.
 */
data class CategoryDto(
    val id: String,
    val userId: String,
    val name: String,
    val type: CategoryType,
    val icon: String?,
    val color: String?,
    val position: Int,
    val isArchived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isDeleted: Boolean,
    val serverRev: Long?,
)
