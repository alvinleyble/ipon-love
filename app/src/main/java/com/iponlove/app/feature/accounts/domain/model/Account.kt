package com.iponlove.app.feature.accounts.domain.model

import java.math.BigDecimal

/**
 * An account the user holds money in (GCash, a card, cash on hand, …). Pure domain
 * model — no sync columns (`updated_at`, `pending_sync`, …) and no `user_id`; those
 * are data-layer concerns owned by the repository.
 *
 * [openingBalance] is the starting balance. The *current* balance is derived from the
 * ledger (opening + transactions) and is never stored here (ADR-0007); that derivation
 * arrives with the transactions slice.
 */
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val openingBalance: BigDecimal,
    val icon: String? = null,
    val color: String? = null,
    val position: Int = 0,
    val isArchived: Boolean = false,
)
