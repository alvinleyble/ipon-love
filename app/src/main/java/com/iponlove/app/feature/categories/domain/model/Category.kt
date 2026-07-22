package com.iponlove.app.feature.categories.domain.model

/**
 * A label transactions are filed under (Groceries, Salary, …). Pure domain model — no
 * `user_id` and no sync columns; those are data-layer concerns owned by the repository.
 */
data class Category(
    val id: String,
    val name: String,
    val type: CategoryType,
    val icon: String? = null,
    val color: String? = null,
    val position: Int = 0,
    val isArchived: Boolean = false,
    /**
     * True when this is a couple-owned shared category (ADR-0018): it appears in both
     * partners' pickers. Set by the data layer (`couple_id != null`); not editor-writable.
     */
    val isShared: Boolean = false,
    /**
     * True when the current user created this row (`created_by == me`). Gates un-sharing:
     * only the creator may make a shared category personal, else the revert stamps the row
     * with the partner's `user_id` — a row RLS forbids them to push (ADR-0018, v1.6.5 Item 20).
     * Meaningful only while [isShared]; false on personal and legacy no-`created_by` rows.
     */
    val isCreator: Boolean = false,
    /**
     * When true, transactions filed under this category are pass-through money (e.g. a work
     * expense your employer reimburses) — they still show in Records and count toward account
     * balance, but are excluded from Analysis, Budgets, and the couple Combined view (ADR-0049).
     * Editor-writable on both expense and income categories; syncs like any category column.
     */
    val excludeFromAnalysis: Boolean = false,
)
