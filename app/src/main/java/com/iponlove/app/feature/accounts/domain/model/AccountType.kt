package com.iponlove.app.feature.accounts.domain.model

/** Kind of money holder. Mirrors the `account_type` enum in supabase/schema.sql. */
enum class AccountType {
    CASH,
    CARD,
    BANK,
    EWALLET,
}
