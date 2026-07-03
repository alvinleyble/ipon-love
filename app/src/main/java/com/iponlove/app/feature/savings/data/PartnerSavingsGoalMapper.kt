package com.iponlove.app.feature.savings.data

import com.iponlove.app.feature.savings.data.local.SavingsGoalEntity
import com.iponlove.app.feature.savings.data.remote.PartnerSavingsGoalDto
import java.math.BigDecimal

/**
 * Partner-view row → Entity (ADR-0005). Content is nulled when the goal is unshared or deleted;
 * such rows are purged on apply (shouldPurge = !isShared || isDeleted), so the safe defaults for
 * name/target/etc. are never shown. `created_at` is absent from the view — falls back to
 * `updated_at`.
 */
fun PartnerSavingsGoalDto.toEntity(): SavingsGoalEntity = SavingsGoalEntity(
    id = id,
    userId = userId,
    coupleId = coupleId,
    isShared = isShared,
    name = name.orEmpty(),
    targetAmount = targetAmount ?: BigDecimal.ZERO,
    targetDate = targetDate,
    icon = icon,
    color = color,
    isArchived = isArchived ?: false,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
