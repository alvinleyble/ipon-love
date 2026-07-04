package com.iponlove.app.feature.recurring.data

import com.iponlove.app.feature.recurring.data.local.RecurringRuleEntity
import com.iponlove.app.feature.recurring.data.remote.RecurringRuleDto
import com.iponlove.app.feature.recurring.data.remote.RecurringTemplateDto
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.model.RecurringRule
import com.iponlove.app.feature.recurring.domain.model.RecurringTemplate

/** Entity ↔ Domain ↔ DTO conversions. Pure functions — unit-tested. */

fun RecurringRuleEntity.toDomain(): RecurringRule = RecurringRule(
    id = id,
    frequency = frequency,
    interval = interval,
    nextDate = nextDate,
    endDate = endDate,
    template = RecurringTemplate(
        amount = templateAmount,
        accountId = templateAccountId,
        categoryId = templateCategoryId,
        note = templateNote,
    ),
    isPaused = isPaused,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002); nests the template. */
fun RecurringRuleEntity.toDto(): RecurringRuleDto = RecurringRuleDto(
    id = id,
    userId = userId,
    frequency = frequency.name,
    interval = interval,
    nextDate = nextDate,
    endDate = endDate,
    template = RecurringTemplateDto(
        amount = templateAmount,
        accountId = templateAccountId,
        categoryId = templateCategoryId,
        note = templateNote,
    ),
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    isPaused = isPaused,
)

/**
 * DTO → Entity for a pulled row: server-canonical, so `pendingSync = false`, carrying the
 * server-assigned `serverRev` the cursor advances on (ADR-0002). Flattens the template.
 */
fun RecurringRuleDto.toEntity(): RecurringRuleEntity = RecurringRuleEntity(
    id = id,
    userId = userId,
    frequency = RecurringFrequency.valueOf(frequency),
    interval = interval,
    nextDate = nextDate,
    endDate = endDate,
    templateAmount = template.amount,
    templateAccountId = template.accountId,
    templateCategoryId = template.categoryId,
    templateNote = template.note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
    isPaused = isPaused,
)
