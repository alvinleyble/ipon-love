package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.date.PH_ZONE
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.ReceiptDuplicateDetector
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Tier-1 coverage for the soft duplicate-scan warning (v1.7.3 Item 2 Slice 2, ADR-0062
 * Consequences). It warns and never blocks, so the cost of a miss is a duplicate row and the cost
 * of a false positive is a wrong accusation — both are worth pinning.
 */
class ReceiptDuplicateDetectorTest {

    private val scanDay = LocalDate.of(2026, 8, 5)
    private val scanDate = phInstant(scanDay, LocalTime.of(12, 0))

    private fun phInstant(day: LocalDate, time: LocalTime = LocalTime.NOON): Instant =
        day.atTime(time).atZone(PH_ZONE).toInstant()

    private fun existing(
        id: String = "existing",
        amount: String = "750.00",
        date: Instant = scanDate,
    ) = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amount = BigDecimal(amount),
        accountId = "acc-1",
        categoryId = "cat-1",
        date = date,
    )

    private fun find(
        amount: String = "750.00",
        date: Instant = scanDate,
        candidates: List<Transaction>,
        excludeId: String = "draft",
    ) = ReceiptDuplicateDetector.findDuplicate(
        amount = BigDecimal(amount),
        date = date,
        candidates = candidates,
        excludeId = excludeId,
        zone = PH_ZONE,
    )

    @Test
    fun `a same-amount same-day expense is flagged`() {
        assertThat(find(candidates = listOf(existing()))?.id).isEqualTo("existing")
    }

    @Test
    fun `an amount written at a different scale still matches`() {
        // The reason the amount comparison can't be pushed into SQL: money is persisted as a plain
        // string, so "750" and "750.00" are equal in value but unequal to both `equals` and SQL.
        assertThat(find(amount = "750", candidates = listOf(existing(amount = "750.00")))).isNotNull()
    }

    @Test
    fun `a different amount is not flagged`() {
        assertThat(find(candidates = listOf(existing(amount = "751.00")))).isNull()
    }

    @Test
    fun `yesterday and tomorrow are within the window`() {
        assertThat(find(candidates = listOf(existing(date = phInstant(scanDay.minusDays(1))))))
            .isNotNull()
        assertThat(find(candidates = listOf(existing(date = phInstant(scanDay.plusDays(1))))))
            .isNotNull()
    }

    @Test
    fun `two days out is outside the window`() {
        assertThat(find(candidates = listOf(existing(date = phInstant(scanDay.minusDays(2))))))
            .isNull()
    }

    @Test
    fun `the window is calendar days in PH time, not a rolling 24 hours`() {
        // 23:30 the night before is barely half an hour away but is still the previous day; the
        // day after next at 00:30 is closer in clock time than 2 days yet must not match.
        val lateLastNight = phInstant(scanDay.minusDays(1), LocalTime.of(23, 30))
        assertThat(find(date = phInstant(scanDay, LocalTime.of(0, 15)), candidates = listOf(existing(date = lateLastNight))))
            .isNotNull()
        val earlyTwoDaysOn = phInstant(scanDay.plusDays(2), LocalTime.of(0, 30))
        assertThat(find(date = phInstant(scanDay, LocalTime.of(23, 45)), candidates = listOf(existing(date = earlyTwoDaysOn))))
            .isNull()
    }

    @Test
    fun `the draft's own row is never its own duplicate`() {
        assertThat(find(candidates = listOf(existing(id = "draft")), excludeId = "draft")).isNull()
    }

    @Test
    fun `the most recent of several matches is the one named`() {
        val older = existing(id = "older", date = phInstant(scanDay.minusDays(1)))
        val newer = existing(id = "newer", date = scanDate)
        assertThat(find(candidates = listOf(older, newer))?.id).isEqualTo("newer")
    }

    @Test
    fun `nothing on the books means no warning`() {
        assertThat(find(candidates = emptyList())).isNull()
    }
}
