package com.iponlove.app.feature.transactions.domain.model

/** Direction of money movement. Mirrors the `transaction_type` enum in schema. */
enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
}
