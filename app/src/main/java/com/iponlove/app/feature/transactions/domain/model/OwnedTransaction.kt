package com.iponlove.app.feature.transactions.domain.model

/**
 * A [Transaction] tagged with the id of the member who owns it. The plain [Transaction]
 * model is deliberately ownerless (it's the user's own ledger); the combined couple view
 * (ADR-0011) is the one place attribution matters, so it carries [ownerId] alongside.
 */
data class OwnedTransaction(
    val ownerId: String,
    val transaction: Transaction,
)
