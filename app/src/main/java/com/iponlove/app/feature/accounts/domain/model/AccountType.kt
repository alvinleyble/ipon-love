package com.iponlove.app.feature.accounts.domain.model

import kotlinx.serialization.Serializable

/** Kind of money holder. Mirrors the `account_type` enum in supabase/schema.sql. */
@Serializable
enum class AccountType {
    CASH,
    CARD,
    BANK,
    EWALLET,
}
