package com.iponlove.app.feature.drafts.data

import com.iponlove.app.feature.drafts.data.local.TransactionDraftEntity
import com.iponlove.app.feature.drafts.data.remote.TransactionDraftDto
import com.iponlove.app.feature.drafts.domain.model.TransactionDraft

/** Entity ↔ Domain ↔ DTO conversions for parked drafts. Pure functions — unit-tested. */

fun TransactionDraftEntity.toDomain(): TransactionDraft = TransactionDraft(
    id = id,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    toAccountId = toAccountId,
    note = note,
    date = date,
    isPrivate = isPrivate,
    receiptCount = receiptCount,
    localImageIds = localImageIds,
    parkedAt = createdAt,
)

/**
 * Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002) **and `localImageIds`**
 * (local-only, ADR-0066 decision 1 — the row syncs, the photos don't).
 */
fun TransactionDraftEntity.toDto(): TransactionDraftDto = TransactionDraftDto(
    id = id,
    userId = userId,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    toAccountId = toAccountId,
    note = note,
    date = date,
    isPrivate = isPrivate,
    receiptCount = receiptCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

/**
 * DTO → Entity for a pulled row: server-canonical, so `pendingSync = false`, carrying the
 * server-assigned `serverRev` the cursor advances on (ADR-0002).
 *
 * `localImageIds` comes back **empty** — correct for a draft authored on another device, whose
 * files are not on this one. The DAO's `applyPullBatch` re-attaches this device's own ids when
 * the row is one it already holds, so a remote edit can't strand local photos.
 */
fun TransactionDraftDto.toEntity(): TransactionDraftEntity = TransactionDraftEntity(
    id = id,
    userId = userId,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    toAccountId = toAccountId,
    note = note,
    date = date,
    isPrivate = isPrivate,
    receiptCount = receiptCount,
    localImageIds = emptyList(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
