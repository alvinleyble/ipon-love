package com.iponlove.app.feature.transactions.data

import com.iponlove.app.feature.transactions.data.local.TransactionEntity
import com.iponlove.app.feature.transactions.data.remote.PartnerTransactionDto
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal

/**
 * Partner-view row → Entity (ADR-0005). The view nulls content columns (type, amount, ids,
 * note, date) when the row is private or deleted; those rows are hard-deleted on apply, so
 * defaulting the nulls is safe. `recurring_rule_id`/`created_at` are absent from the view.
 */
fun PartnerTransactionDto.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    userId = userId,
    type = type ?: TransactionType.EXPENSE,
    amount = amount ?: BigDecimal.ZERO,
    accountId = accountId.orEmpty(),
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note,
    date = date ?: updatedAt,
    isPrivate = isPrivate,
    recurringRuleId = null,
    attachmentUrl = attachmentUrl,
    isSettlement = isSettlement,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
