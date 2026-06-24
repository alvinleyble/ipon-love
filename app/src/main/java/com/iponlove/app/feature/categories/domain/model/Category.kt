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
)
