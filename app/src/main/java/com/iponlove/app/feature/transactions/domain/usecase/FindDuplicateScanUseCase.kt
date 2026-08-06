package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.core.date.PH_ZONE
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * The soft duplicate-scan check (v1.7.3 Item 2 Slice 2, ADR-0062 Consequences): scanning the same
 * receipt twice used to produce two identical transactions with no warning at all.
 *
 * Reads a window two days either side — wider than the ±1 *calendar* day rule, because a day
 * boundary in [PH_ZONE] can sit up to a day away from the raw instant either way — and lets
 * [ReceiptDuplicateDetector] apply the real rule.
 */
class FindDuplicateScanUseCase @Inject constructor(
    private val transactions: TransactionRepository,
) {
    suspend operator fun invoke(amount: BigDecimal, date: Instant, excludeId: String): Transaction? {
        val candidates = transactions.getOwnExpensesBetween(
            startInclusive = date.minus(WINDOW_DAYS, ChronoUnit.DAYS),
            endExclusive = date.plus(WINDOW_DAYS, ChronoUnit.DAYS),
        )
        return ReceiptDuplicateDetector.findDuplicate(amount, date, candidates, excludeId, PH_ZONE)
    }

    private companion object {
        const val WINDOW_DAYS = 2L
    }
}
