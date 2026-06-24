package com.iponlove.app.feature.budgets.data.remote

import java.math.BigDecimal
import java.time.Instant

/**
 * Wire shape of a `budgets` row for Supabase. Omits `pendingSync` (local-only,
 * ADR-0002). Serialization annotations arrive with the Supabase slice.
 */
data class BudgetDto(
    val id: String,
    val userId: String?,
    val coupleId: String?,
    val categoryId: String?,
    val amount: BigDecimal,
    val yearMonth: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isDeleted: Boolean,
    val serverRev: Long?,
)
