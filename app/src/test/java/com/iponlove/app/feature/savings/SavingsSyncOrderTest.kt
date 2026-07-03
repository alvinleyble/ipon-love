package com.iponlove.app.feature.savings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.SyncTable
import org.junit.Test

/**
 * FK push/pull ordering for the savings tables (ADR-0009/0025). Push is sequential in
 * [SyncTable] ordinal order, so a parent must precede its child; pull is parallel, but keeping
 * the same relative order documents the dependency.
 */
class SavingsSyncOrderTest {

    @Test
    fun goalPrecedesContributions_forFkParentBeforeChild() {
        assertThat(SyncTable.SAVINGS_GOALS.ordinal)
            .isLessThan(SyncTable.GOAL_CONTRIBUTIONS.ordinal)
    }

    @Test
    fun partnerGoalPrecedesPartnerContributions() {
        assertThat(SyncTable.PARTNER_SAVINGS_GOALS.ordinal)
            .isLessThan(SyncTable.PARTNER_GOAL_CONTRIBUTIONS.ordinal)
    }

    @Test
    fun ownedGoalsComeAfterBudgets_andBeforeTheirPartnerViews() {
        // "Inserted after budgets" (ADR-0025); owned tables precede their redacting partner views.
        assertThat(SyncTable.SAVINGS_GOALS.ordinal).isGreaterThan(SyncTable.BUDGETS.ordinal)
        assertThat(SyncTable.GOAL_CONTRIBUTIONS.ordinal)
            .isLessThan(SyncTable.PARTNER_SAVINGS_GOALS.ordinal)
    }
}
