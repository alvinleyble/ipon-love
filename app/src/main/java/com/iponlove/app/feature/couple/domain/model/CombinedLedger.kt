package com.iponlove.app.feature.couple.domain.model

import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

/**
 * The combined couple view (ADR-0011): a merged, owner-attributed transaction stream plus
 * each member's spend for the current month. Shows shared *spending*, never partner
 * balances — only the redacted, non-private rows that crossed via the partner views reach
 * here (ADR-0004/0005).
 */
data class CombinedLedger(
    val entries: List<CombinedEntry>,
    val members: List<MemberSpend>,
)

/**
 * One transaction in the merged stream. [ownerId] attributes it to a member (the UI looks
 * its color/name up from [CombinedLedger.members]); [title] is the resolved category name,
 * "Transfer", or "Uncategorized". [attachmentUrls] are the receipt images, if any (up to 3) —
 * partner rows carry them too (partner_transaction_images view), viewable under the receipts
 * partner-read Storage policy.
 */
data class CombinedEntry(
    val id: String,
    val ownerId: String,
    val type: TransactionType,
    val amount: BigDecimal,
    val title: String,
    val date: Instant,
    val isMine: Boolean,
    val attachmentUrls: List<String> = emptyList(),
)

/** A member's identity plus their total EXPENSE for the current month (the spending chip). */
data class MemberSpend(
    val id: String,
    val displayName: String?,
    val accentColor: String?,
    val isMine: Boolean,
    val monthlyExpense: BigDecimal,
)
