package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.Transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Finds an already-recorded transaction that looks like the one just scanned (v1.7.3 Item 2
 * Slice 2, ADR-0062 Consequences): same type, same amount, within ±1 calendar day.
 *
 * **It warns; it never blocks.** Save stays fully enabled when this fires — a legitimate second
 * same-day expense (two jeepney fares, two coffees) is ordinary, and a false positive that refused
 * Save would be worse than the duplicate it prevented.
 *
 * Pure, like [ReceiptHistoryMatcher] — the caller supplies the candidate rows and the zone.
 */
object ReceiptDuplicateDetector {

    /**
     * The most recent candidate matching [amount] on a day within one of [date]'s, or null.
     *
     * **Amounts are compared with [BigDecimal.compareTo], never `==`.** Money is persisted as a
     * plain string ([com.iponlove.app.core.database.converters.IponConverters]), so `750` and
     * `750.00` are different scales — equal in value, unequal to both `equals` and to SQL, which is
     * why this comparison cannot be pushed down into the query.
     *
     * [excludeId] drops the draft's own row, which matters when a scan lands on a transaction that
     * has already been saved once.
     */
    fun findDuplicate(
        amount: BigDecimal,
        date: Instant,
        candidates: List<Transaction>,
        excludeId: String,
        zone: ZoneId,
    ): Transaction? {
        val day = date.atZone(zone).toLocalDate()
        return candidates
            .filter { candidate ->
                candidate.id != excludeId &&
                    candidate.amount.compareTo(amount) == 0 &&
                    abs(ChronoUnit.DAYS.between(day, candidate.date.atZone(zone).toLocalDate())) <= 1
            }
            .maxByOrNull { it.date }
    }
}
