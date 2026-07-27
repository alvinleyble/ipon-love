package com.iponlove.app.core.sync

/**
 * The synced tables, listed in FK-dependency order (parent → child).
 *
 * Sync processes tables in this order **both directions** (ADR-0009):
 *  - Push parent→child, so a parent receives a lower `server_rev` than its child.
 *  - Pull parent→child, so a child's parent is already present before it lands.
 *
 * The ordinal IS the order — keep declaration order meaningful. [SyncEngine] sorts
 * the contributed [TableSyncer]s by [ordinal], so DI contribution order is irrelevant.
 */
enum class SyncTable {
    USERS,
    COUPLES,
    ACCOUNTS,
    CATEGORIES,
    RECURRING_RULES,
    TRANSACTIONS,
    // Receipt photos (up to 3 per transaction); FK child of transactions, mirrors NOTE_IMAGES.
    TRANSACTION_IMAGES,
    BUDGETS,
    // Couple-shared, read/write by both partners (not redacted views) — FK children of
    // couples+users, and payments of debts. Named for the `partner_debts` tables, distinct
    // from the redacted PARTNER_* views below.
    PARTNER_DEBTS,
    DEBT_PAYMENTS,
    NOTES,
    NOTE_IMAGES,
    // Savings goals + their append-only contribution ledger (ADR-0025). Goal (parent) before
    // contributions (child) so push lands the FK parent first.
    SAVINGS_GOALS,
    GOAL_CONTRIBUTIONS,
    // Partner views pull after the matching owned tables so FK parents are already present.
    PARTNER_ACCOUNTS,
    PARTNER_CATEGORIES,
    PARTNER_TRANSACTIONS,
    PARTNER_TRANSACTION_IMAGES,
    PARTNER_NOTES,
    PARTNER_NOTE_IMAGES,
    // Partner's shared goals + their contributions (redacting views, ADR-0005).
    PARTNER_SAVINGS_GOALS,
    PARTNER_GOAL_CONTRIBUTIONS,
    // Notification inbox — a leaf depending only on the users row, and own-user-only (no
    // partner variant, never replicated). Last in the order because nothing depends on it
    // and it must never delay a financial row's push (ADR-0053).
    NOTIFICATIONS,
}
