package com.iponlove.app.feature.savings.data

import com.iponlove.app.feature.savings.data.local.GoalContributionEntity
import com.iponlove.app.feature.savings.data.remote.PartnerGoalContributionDto
import java.math.BigDecimal
import java.time.Instant

/**
 * Partner-view row → Entity (ADR-0005). The view nulls `amount`/`note`/`date` when the
 * contribution is deleted or its parent goal is unshared/deleted. Since the entity's `amount`
 * is non-null money, a redacted (null-amount) row is folded into `isDeleted = true` so the
 * partner syncer purges it (shouldPurge = isDeleted). `created_at` is absent from the view.
 */
fun PartnerGoalContributionDto.toEntity(): GoalContributionEntity {
    val redacted = amount == null
    return GoalContributionEntity(
        id = id,
        goalId = goalId,
        userId = userId,
        amount = amount ?: BigDecimal.ZERO,
        note = note,
        date = date ?: Instant.EPOCH,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted || redacted,
        serverRev = serverRev,
        pendingSync = false,
    )
}
