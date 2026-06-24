package com.iponlove.app.feature.categories.domain.model

/** Whether a category groups money coming in or going out. Mirrors `category_type` in schema. */
enum class CategoryType {
    INCOME,
    EXPENSE,
}
