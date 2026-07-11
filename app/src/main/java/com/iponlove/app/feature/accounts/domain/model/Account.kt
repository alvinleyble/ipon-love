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
    /**
     * True when this is a couple-owned shared account (ADR-0018): both partners log against
     * it and see its balance. Set by the data layer (`couple_id != null`); the editor never
     * writes it — sharing is a separate action (share/un-share), not an editable field.
     */
    val isShared: Boolean = false,
    /**
     * True when the current user created this row (`created_by == me`). Only unsharing is
     * gated on it: a shared account reverts to its creator, so only the creator may un-share
     * — a non-creator's revert would stamp the row with the *other* partner's `user_id`, a row
     * RLS forbids them to push, wedging sync (ADR-0018, v1.6.5 Item 20). Meaningful only while
     * [isShared]; false on personal rows and on legacy shared rows with no `created_by`.
     */
    val isCreator: Boolean = false,
)
