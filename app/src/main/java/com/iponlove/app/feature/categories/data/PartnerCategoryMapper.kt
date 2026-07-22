package com.iponlove.app.feature.categories.data

import com.iponlove.app.feature.categories.data.local.CategoryEntity
import com.iponlove.app.feature.categories.data.remote.PartnerCategoryDto
import com.iponlove.app.feature.categories.domain.model.CategoryType

/**
 * Partner-view row → Entity (ADR-0005). The view nulls content columns when the row is
 * deleted; those rows are hard-deleted on apply, so defaulting the nulls is safe.
 * `position`/`created_at` are absent from the view and not needed for partner categories.
 */
fun PartnerCategoryDto.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    userId = userId,
    // Partner replicas are personal-to-the-partner rows, never couple-owned (those cross via
    // the base table, ADR-0018), so they carry no couple_id/created_by here.
    coupleId = null,
    createdBy = null,
    name = name.orEmpty(),
    type = type ?: CategoryType.EXPENSE,
    icon = icon,
    color = color,
    position = 0,
    isArchived = isArchived ?: false,
    excludeFromAnalysis = excludeFromAnalysis ?: false,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
