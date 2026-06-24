package com.iponlove.app.feature.categories.domain.model

import kotlinx.serialization.Serializable

/** Whether a category groups money coming in or going out. Mirrors `category_type` in schema. */
@Serializable
enum class CategoryType {
    INCOME,
    EXPENSE,
}
