package com.iponlove.app.feature.savings.data

import com.iponlove.app.feature.savings.data.local.GoalContributionEntity
import com.iponlove.app.feature.savings.data.remote.GoalContributionDto
import com.iponlove.app.feature.savings.domain.model.GoalContribution

/** Entity ↔ Domain ↔ DTO conversions. Pure functions — unit-tested. */

/** [currentUserId] drives [GoalContribution.isMine] — only your own rows are editable and are
 *  attributed as "You" in the ledger; a partner's row is read-only. */
fun GoalContributionEntity.toDomain(currentUserId: String): GoalContribution = GoalContribution(
    id = id,
    goalId = goalId,
    amount = amount,
    note = note,
    date = date,
    byUserId = userId,
    isMine = userId == currentUserId,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002). */
fun GoalContributionEntity.toDto(): GoalContributionDto = GoalContributionDto(
    id = id,
    goalId = goalId,
    userId = userId,
    amount = amount,
    note = note,
    date = date,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

/** DTO → Entity for a pulled row: server-canonical, so `pendingSync = false` (ADR-0002). */
fun GoalContributionDto.toEntity(): GoalContributionEntity = GoalContributionEntity(
    id = id,
    goalId = goalId,
    userId = userId,
    amount = amount,
    note = note,
    date = date,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
