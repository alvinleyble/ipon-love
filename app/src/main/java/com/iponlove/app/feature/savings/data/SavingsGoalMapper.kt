package com.iponlove.app.feature.savings.data

import com.iponlove.app.feature.savings.data.local.SavingsGoalEntity
import com.iponlove.app.feature.savings.data.remote.SavingsGoalDto
import com.iponlove.app.feature.savings.domain.model.SavingsGoal

/** Entity ↔ Domain ↔ DTO conversions. Pure functions — unit-tested. */

/** [currentUserId] drives [SavingsGoal.isPartnerGoal] — a goal owned by another user is a
 *  replicated partner goal (ADR-0005) whose metadata is read-only. */
fun SavingsGoalEntity.toDomain(currentUserId: String): SavingsGoal = SavingsGoal(
    id = id,
    name = name,
    targetAmount = targetAmount,
    targetDate = targetDate,
    icon = icon,
    color = color,
    isShared = isShared,
    isArchived = isArchived,
    isPartnerGoal = userId != currentUserId,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002). */
fun SavingsGoalEntity.toDto(): SavingsGoalDto = SavingsGoalDto(
    id = id,
    userId = userId,
    coupleId = coupleId,
    isShared = isShared,
    name = name,
    targetAmount = targetAmount,
    targetDate = targetDate,
    icon = icon,
    color = color,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

/** DTO → Entity for a pulled row: server-canonical, so `pendingSync = false` (ADR-0002). */
fun SavingsGoalDto.toEntity(): SavingsGoalEntity = SavingsGoalEntity(
    id = id,
    userId = userId,
    coupleId = coupleId,
    isShared = isShared,
    name = name,
    targetAmount = targetAmount,
    targetDate = targetDate,
    icon = icon,
    color = color,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
