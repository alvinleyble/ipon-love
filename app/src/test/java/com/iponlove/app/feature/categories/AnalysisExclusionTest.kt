package com.iponlove.app.feature.categories

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.AnalysisExclusion
import org.junit.Test

/** Pass-through category exclusion feeding Analysis/Budgets/Combined (ADR-0049). */
class AnalysisExclusionTest {

    private fun cat(id: String, exclude: Boolean) =
        Category(id = id, name = id, type = CategoryType.EXPENSE, excludeFromAnalysis = exclude)

    // Minimal row standing in for any transaction-like type the calculators consume.
    private data class Row(val id: String, val categoryId: String?)

    @Test
    fun excludedIds_collectsOnlyFlaggedCategories() {
        val ids = AnalysisExclusion.excludedIds(
            listOf(cat("reimbursable", true), cat("groceries", false), cat("reimbursement", true)),
        )
        assertThat(ids).containsExactly("reimbursable", "reimbursement")
    }

    @Test
    fun retainAnalyzable_dropsRowsInExcludedCategories_keepsTheRest() {
        val rows = listOf(
            Row("a", "groceries"),
            Row("b", "reimbursable"),
            Row("c", null),            // uncategorized / transfer / settlement — never excluded
            Row("d", "reimbursement"),
        )

        val kept = AnalysisExclusion.retainAnalyzable(rows, setOf("reimbursable", "reimbursement")) { it.categoryId }

        assertThat(kept.map { it.id }).containsExactly("a", "c").inOrder()
    }

    @Test
    fun retainAnalyzable_noExclusions_returnsInputUnchanged() {
        val rows = listOf(Row("a", "groceries"), Row("b", "reimbursable"))

        val kept = AnalysisExclusion.retainAnalyzable(rows, emptySet()) { it.categoryId }

        assertThat(kept).isEqualTo(rows)
    }
}
