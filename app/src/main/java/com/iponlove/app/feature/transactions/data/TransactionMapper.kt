package com.iponlove.app.feature.transactions.data

import com.iponlove.app.feature.transactions.data.local.TransactionEntity
import com.iponlove.app.feature.transactions.data.remote.TransactionDto
import com.iponlove.app.feature.transactions.domain.model.Transaction

/** Entity ↔ Domain ↔ DTO conversions. Pure functions — unit-tested. */

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    type = type,
    amount = amount,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note,
    date = date,
    isPrivate = isPrivate,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002). */
fun TransactionEntity.toDto(): TransactionDto = TransactionDto(
    id = id,
    userId = userId,
    type = type,
    amount = amount,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note,
    date = date,
    isPrivate = isPrivate,
    recurringRuleId = recurringRuleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

/**
 * DTO → Entity for a pulled row: server-canonical, so `pendingSync = false`, carrying
 * the server-assigned `serverRev` the cursor advances on (ADR-0002).
 */
fun TransactionDto.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    userId = userId,
    type = type,
    amount = amount,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note,
    date = date,
    isPrivate = isPrivate,
    recurringRuleId = recurringRuleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
