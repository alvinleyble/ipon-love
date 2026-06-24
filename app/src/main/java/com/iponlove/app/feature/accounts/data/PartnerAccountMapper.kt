package com.iponlove.app.feature.accounts.data

import com.iponlove.app.feature.accounts.data.local.AccountEntity
import com.iponlove.app.feature.accounts.data.remote.PartnerAccountDto
import com.iponlove.app.feature.accounts.domain.model.AccountType
import java.math.BigDecimal

/**
 * Partner-view row → Entity (ADR-0005). The view nulls content columns when the row is
 * deleted; those rows are hard-deleted on apply (never persisted), so defaulting the nulls
 * here is safe. `opening_balance` is absent from the view — partner balances are never
 * shown (ADR-0011) — so a replicated partner account always carries 0 locally.
 */
fun PartnerAccountDto.toEntity(): AccountEntity = AccountEntity(
    id = id,
    userId = userId,
    name = name.orEmpty(),
    type = type ?: AccountType.CASH,
    openingBalance = BigDecimal.ZERO,
    icon = icon,
    color = color,
    position = 0,
    isArchived = isArchived ?: false,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    serverRev = serverRev,
    pendingSync = false,
)
