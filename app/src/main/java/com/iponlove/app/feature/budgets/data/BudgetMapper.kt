package com.iponlove.app.feature.budgets.data

import com.iponlove.app.feature.budgets.data.local.BudgetEntity
import com.iponlove.app.feature.budgets.data.remote.BudgetDto
import com.iponlove.app.feature.budgets.domain.model.Budget

/** Entity ↔ Domain ↔ DTO conversions. Pure functions — unit-tested. */

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    amount = amount,
    yearMonth = yearMonth,
    rolloverEnabled = rolloverEnabled,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002). */
fun BudgetEntity.toDto(): BudgetDto = BudgetDto(
    id = id,
    userId = userId,
    coupleId = coupleId,
    categoryId = categoryId,
    amount = amount,
    yearMonth = yearMonth,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    rolloverEnabled = rolloverEnabled,
)

/**
 * DTO → Entity for a pulled row: server-canonical, so `pendingSync = false`, carrying
 * the server-assigned `serverRev` the cursor advances on (ADR-0002).
 */
fun BudgetDto.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    userId = userId,
    coupleId = coupleId,
    categoryId = categoryId,
    amount = amount,
    yearMonth = yearMonth,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
    rolloverEnabled = rolloverEnabled,
)
