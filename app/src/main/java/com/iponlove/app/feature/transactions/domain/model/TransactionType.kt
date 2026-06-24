package com.iponlove.app.feature.transactions.domain.model

import kotlinx.serialization.Serializable

/** Direction of money movement. Mirrors the `transaction_type` enum in schema. */
@Serializable
enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
}
