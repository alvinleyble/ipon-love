package com.iponlove.app.feature.categories.domain.usecase

import com.iponlove.app.feature.categories.domain.model.Category

/**
 * Pass-through category handling (ADR-0049). A category flagged [Category.excludeFromAnalysis]
 * — e.g. reimbursable work expenses your employer pays back — means its transactions are hidden
 * from **Analysis, Budgets, and the couple Combined view**, while still showing in Records and
 * counting toward account balance.
 *
 * Pure, so the Analysis/Budget/Combined calculators stay category-unaware: a caller that already
 * observes both transactions and categories derives [excludedIds] once and runs its transaction
 * list through [retainAnalyzable] before handing it to a calculator. Generic in the row type so
 * it serves both `Transaction` and the couple view's `OwnedTransaction`.
 */
object AnalysisExclusion {

    /** Ids of the categories flagged pass-through. */
    fun excludedIds(categories: List<Category>): Set<String> =
        categories.asSequence().filter { it.excludeFromAnalysis }.map { it.id }.toSet()

    /**
     * The subset of [items] not filed under an excluded category. [categoryOf] extracts each
     * row's category id (null — transfers/settlements/uncategorized — is never excluded).
     * Returns [items] unchanged when nothing is excluded (the common case).
     */
    fun <T> retainAnalyzable(items: List<T>, excludedIds: Set<String>, categoryOf: (T) -> String?): List<T> =
        if (excludedIds.isEmpty()) items else items.filterNot { categoryOf(it) in excludedIds }
}
