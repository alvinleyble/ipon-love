package com.iponlove.app.feature.recurring.data.remote

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Wire shape of a `recurring_rules` row for Supabase. The schema stores the rule's fixed
 * details as a `template` jsonb, so the nested [RecurringTemplateDto] maps to that column.
 * Omits `pendingSync` (local-only, ADR-0002). Serialization annotations arrive with the
 * Supabase slice.
 */
data class RecurringRuleDto(
    val id: String,
    val userId: String,
    val frequency: String,
    val interval: Int,
    val nextDate: LocalDate,
    val endDate: LocalDate?,
    val template: RecurringTemplateDto,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isDeleted: Boolean,
    val serverRev: Long?,
)

/** Maps to the rule's `template` jsonb (amount, account_id, category_id, note). */
data class RecurringTemplateDto(
    val amount: BigDecimal,
    val accountId: String,
    val categoryId: String,
    val note: String?,
)
