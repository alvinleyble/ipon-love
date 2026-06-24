package com.iponlove.app.feature.categories.data

import com.iponlove.app.feature.categories.data.local.CategoryEntity
import com.iponlove.app.feature.categories.data.remote.CategoryDto
import com.iponlove.app.feature.categories.domain.model.Category

/** Entity ↔ Domain ↔ DTO conversions. Pure functions — unit-tested. */

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = type,
    icon = icon,
    color = color,
    position = position,
    isArchived = isArchived,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002). */
fun CategoryEntity.toDto(): CategoryDto = CategoryDto(
    id = id,
    userId = userId,
    name = name,
    type = type,
    icon = icon,
    color = color,
    position = position,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

/**
 * DTO → Entity for a pulled row: server-canonical, so `pendingSync = false`, carrying
 * the server-assigned `serverRev` the cursor advances on (ADR-0002).
 */
fun CategoryDto.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    userId = userId,
    name = name,
    type = type,
    icon = icon,
    color = color,
    position = position,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
