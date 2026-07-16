package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.recurring.domain.model.PendingConfirmation
import com.iponlove.app.feature.recurring.presentation.components.skippableOccurrenceIds
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Only each rule's OLDEST pending occurrence may be skipped (Item 37) — skipping a later one
 * would strand the earlier occurrence, since Skip advances a single cursor. Confirm has no such
 * restriction (order-independent via the materialized-id exclusion).
 */
class SkippableOccurrenceIdsTest {

    @Test fun empty_isEmpty() {
        assertThat(skippableOccurrenceIds(emptyList())).isEmpty()
    }

    @Test fun singleOccurrence_isSkippable() {
        val items = listOf(pc("r", "r-1", 1))
        assertThat(skippableOccurrenceIds(items)).containsExactly("r-1")
    }

    @Test fun multipleOccurrencesOfOneRule_onlyOldestIsSkippable() {
        // Oldest-first: r-1 (oldest) is skippable; the later two are not.
        val items = listOf(pc("r", "r-1", 1), pc("r", "r-2", 2), pc("r", "r-3", 3))
        assertThat(skippableOccurrenceIds(items)).containsExactly("r-1")
    }

    @Test fun multipleRules_eachRulesOldestIsSkippable() {
        // Interleaved oldest-first across rules; each rule contributes its first (oldest).
        val items = listOf(
            pc("rent", "rent-1", 1),
            pc("salary", "salary-1", 2),
            pc("rent", "rent-2", 3),
            pc("salary", "salary-2", 4),
        )
        assertThat(skippableOccurrenceIds(items)).containsExactly("rent-1", "salary-1")
    }

    private fun pc(ruleId: String, occId: String, day: Int) = PendingConfirmation(
        ruleId = ruleId,
        occurrenceId = occId,
        date = LocalDate.of(2026, 6, day),
        amount = BigDecimal("1000"),
        type = TransactionType.EXPENSE,
        categoryId = "cat",
        categoryName = "Cat",
        accountId = "acc",
        note = null,
    )
}
