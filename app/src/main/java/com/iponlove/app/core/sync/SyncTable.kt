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
    BUDGETS,
    NOTES,
    NOTE_IMAGES,
}
