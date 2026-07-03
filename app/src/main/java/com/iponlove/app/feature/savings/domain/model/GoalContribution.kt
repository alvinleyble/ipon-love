package com.iponlove.app.feature.savings.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * One entry in a goal's append-only ledger (ADR-0025). [byUserId] is the contributor; [isMine]
 * is derived at the domain boundary (the current user authored it) so only your own rows are
 * editable/deletable and the ledger can attribute "You" vs the partner.
 */
data class GoalContribution(
    val id: String,
    val goalId: String,
    val amount: BigDecimal,
    val note: String?,
    val date: Instant,
    val byUserId: String,
    val isMine: Boolean,
)
