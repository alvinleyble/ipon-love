package com.iponlove.app.feature.accounts.data.remote

import com.iponlove.app.feature.accounts.domain.model.AccountType
import java.math.BigDecimal
import java.time.Instant

/**
 * Wire shape of an `accounts` row for Supabase. Mirrors the server columns and
 * deliberately omits `pendingSync` — that flag is local-only and never leaves the
 * device (ADR-0002). Serialization annotations arrive with the Supabase slice.
 */
data class AccountDto(
    val id: String,
    val userId: String,
    val name: String,
    val type: AccountType,
    val openingBalance: BigDecimal,
    val icon: String?,
    val color: String?,
    val position: Int,
    val isArchived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isDeleted: Boolean,
    val serverRev: Long?,
)
