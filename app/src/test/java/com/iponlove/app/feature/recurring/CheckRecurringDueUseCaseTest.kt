package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.recurring.domain.model.PendingConfirmation
import com.iponlove.app.feature.recurring.domain.usecase.CheckRecurringDueUseCase
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CheckRecurringDueUseCaseTest {

    private val useCase = CheckRecurringDueUseCase()
    private val today = LocalDate.of(2026, 7, 15)

    private fun pending(
        occurrenceId: String,
        date: LocalDate,
        type: TransactionType = TransactionType.EXPENSE,
    ) = PendingConfirmation(
        ruleId = "rule-1",
        occurrenceId = occurrenceId,
        date = date,
        amount = BigDecimal("100.00"),
        type = type,
        categoryId = "cat-1",
        categoryName = "Rent",
        accountId = "acc-1",
        note = null,
    )

    @Test
    fun `fires for an occurrence due today`() {
        val result = useCase(listOf(pending("occ-1", today)), emptySet(), today)
        assertThat(result.map { it.pending.occurrenceId }).containsExactly("occ-1")
    }

    @Test
    fun `fires for an occurrence overdue from an earlier date`() {
        val result = useCase(listOf(pending("occ-1", today.minusDays(5))), emptySet(), today)
        assertThat(result).hasSize(1)
    }

    @Test
    fun `never fires before the due date - no advance warning`() {
        val result = useCase(listOf(pending("occ-1", today.plusDays(1))), emptySet(), today)
        assertThat(result).isEmpty()
    }

    @Test
    fun `does not re-fire an occurrence already raised`() {
        val alreadyRaised = setOf(CheckRecurringDueUseCase.notificationId("occ-1"))
        val result = useCase(listOf(pending("occ-1", today)), alreadyRaised, today)
        assertThat(result).isEmpty()
    }

    @Test
    fun `mixed batch only surfaces the due, unraised occurrences`() {
        val alreadyRaised = setOf(CheckRecurringDueUseCase.notificationId("occ-raised"))
        val result = useCase(
            listOf(
                pending("occ-raised", today),
                pending("occ-future", today.plusDays(3)),
                pending("occ-new", today.minusDays(2)),
            ),
            alreadyRaised,
            today,
        )
        assertThat(result.map { it.pending.occurrenceId }).containsExactly("occ-new")
    }

    /**
     * The occurrence id is the synced dedup contract (ADR-0053 `recurring:{occurrenceId}`) —
     * pin it so it can't silently drift.
     */
    @Test
    fun `notification id is occurrence-prefixed and stable`() {
        assertThat(CheckRecurringDueUseCase.notificationId("occ-1")).isEqualTo("recurring:occ-1")
    }

    @Test
    fun `empty pending list produces no results`() {
        assertThat(useCase(emptyList(), emptySet(), today)).isEmpty()
    }
}
