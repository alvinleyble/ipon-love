package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.MerchantHistoryMatch
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * "Learns from you" — the read half of ADR-0062 decision 5 (v1.7.3 Item 2 Slice 2). Pulls the
 * user's own recent note-bearing expenses and hands them to the pure [ReceiptHistoryMatcher].
 *
 * The UseCase owns the data access (per the standing scalability rule) so the ViewModel never
 * queries; the judgement stays in the matcher so it stays testable without a database.
 */
class InferFromReceiptHistoryUseCase @Inject constructor(
    private val transactions: TransactionRepository,
) {
    suspend operator fun invoke(merchant: String): MerchantHistoryMatch? {
        if (merchant.isBlank()) return null
        return ReceiptHistoryMatcher.match(merchant, transactions.getOwnExpenseHistory(HISTORY_LIMIT))
    }

    companion object {
        /**
         * How far back a scan looks. Bounded because this runs on a tap, on the low-RAM devices
         * this feature targets, for a *suggestion* — and because recency is the right bias anyway:
         * where you shopped this year should outvote where you shopped three years ago.
         */
        const val HISTORY_LIMIT = 500
    }
}
