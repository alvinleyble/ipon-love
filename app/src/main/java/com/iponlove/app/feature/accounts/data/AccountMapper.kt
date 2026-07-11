package com.iponlove.app.feature.accounts.data

import com.iponlove.app.feature.accounts.data.local.AccountEntity
import com.iponlove.app.feature.accounts.data.remote.AccountDto
import com.iponlove.app.feature.accounts.domain.model.Account

/** Entity ↔ Domain ↔ DTO conversions. Pure functions — unit-tested. */

fun AccountEntity.toDomain(currentUserId: String?): Account = Account(
    id = id,
    name = name,
    type = type,
    openingBalance = openingBalance,
    icon = icon,
    color = color,
    position = position,
    isArchived = isArchived,
    isShared = coupleId != null,
    // Creator gate for un-share (ADR-0018): true only when *I* created this row. Null
    // createdBy (legacy pre-created_by shared row) is nobody's to un-share via the UI.
    isCreator = createdBy != null && createdBy == currentUserId,
)

/** Entity → DTO for push. Drops `pendingSync` (local-only, ADR-0002). */
fun AccountEntity.toDto(): AccountDto = AccountDto(
    id = id,
    userId = userId,
    coupleId = coupleId,
    createdBy = createdBy,
    name = name,
    type = type,
    openingBalance = openingBalance,
    icon = icon,
    color = color,
    position = position,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
)

/**
 * DTO → Entity for a pulled row: it is server-canonical, so `pendingSync = false`,
 * and it carries the server-assigned `serverRev` the cursor advances on (ADR-0002).
 */
fun AccountDto.toEntity(): AccountEntity = AccountEntity(
    id = id,
    userId = userId,
    coupleId = coupleId,
    createdBy = createdBy,
    name = name,
    type = type,
    openingBalance = openingBalance,
    icon = icon,
    color = color,
    position = position,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
