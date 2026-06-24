package com.iponlove.app.feature.transactions.data.remote

import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

/**
 * Wire shape of a `transactions` row for Supabase. Omits `pendingSync` (local-only,
 * ADR-0002). Serialization annotations arrive with the Supabase slice.
 */
data class TransactionDto(
    val id: String,
    val userId: String,
    val type: TransactionType,
    val amount: BigDecimal,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    val note: String?,
    val date: Instant,
    val isPrivate: Boolean,
    val recurringRuleId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isDeleted: Boolean,
    val serverRev: Long?,
)
