package com.iponlove.app.feature.export

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.export.domain.ExportScopeLabel
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal

/**
 * Pure-function tests for the export sheet's "What to include" row label (v1.7.0 Item 6,
 * re-grilled 2026-07-24, decision 4): "All transactions" unfiltered, the category name when
 * exactly one category is the *only* constraint, "Filtered" for anything more complex.
 */
class ExportScopeLabelTest {

    private val categoryNames = mapOf("reimb" to "Reimbursable", "food" to "Groceries")

    @Test
    fun `unfiltered reads All transactions`() {
        assertThat(ExportScopeLabel.of(TransactionFilter.NONE, categoryNames))
            .isEqualTo("All transactions")
    }

    @Test
    fun `a single category alone reads its name`() {
        val filter = TransactionFilter(categoryIds = setOf("reimb"))
        assertThat(ExportScopeLabel.of(filter, categoryNames)).isEqualTo("Reimbursable")
    }

    @Test
    fun `two categories reads Filtered`() {
        val filter = TransactionFilter(categoryIds = setOf("reimb", "food"))
        assertThat(ExportScopeLabel.of(filter, categoryNames)).isEqualTo("Filtered")
    }

    @Test
    fun `a single category plus any other constraint reads Filtered`() {
        val filter = TransactionFilter(categoryIds = setOf("reimb"), types = setOf(TransactionType.EXPENSE))
        assertThat(ExportScopeLabel.of(filter, categoryNames)).isEqualTo("Filtered")

        val withAmount = TransactionFilter(categoryIds = setOf("reimb"), minAmount = BigDecimal("100"))
        assertThat(ExportScopeLabel.of(withAmount, categoryNames)).isEqualTo("Filtered")
    }

    @Test
    fun `a non-category-only constraint reads Filtered`() {
        val filter = TransactionFilter(types = setOf(TransactionType.INCOME))
        assertThat(ExportScopeLabel.of(filter, categoryNames)).isEqualTo("Filtered")
    }

    @Test
    fun `an unknown category id falls back to Filtered rather than crashing`() {
        val filter = TransactionFilter(categoryIds = setOf("ghost"))
        assertThat(ExportScopeLabel.of(filter, categoryNames)).isEqualTo("Filtered")
    }
}
