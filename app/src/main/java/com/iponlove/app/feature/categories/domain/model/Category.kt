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
)
