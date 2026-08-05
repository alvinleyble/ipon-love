package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.presentation.RecordsSelection
import com.iponlove.app.feature.transactions.presentation.TransactionsUiState
import org.junit.Test

/** Records' multi-select rules (v1.7.3 Item 7 / ADR-0064 decision 6). */
class RecordsSelectionTest {

    @Test
    fun longPress_entersSelectionModeWithThePressedRowTicked() {
        assertThat(RecordsSelection.begin("a")).containsExactly("a")
    }

    @Test
    fun longPress_onASecondRow_replacesRatherThanAppends() {
        // A long-press is always "start here"; adding to an existing selection is what tap does.
        assertThat(RecordsSelection.begin("b")).containsExactly("b")
    }

    @Test
    fun tap_ticksAndUnticks() {
        val once = RecordsSelection.toggle(setOf("a"), "b")
        assertThat(once).containsExactly("a", "b")
        assertThat(RecordsSelection.toggle(once, "a")).containsExactly("b")
    }

    @Test
    fun untickingTheLastRow_leavesTheSelectionEmpty_whichIsHowSelectionModeExits() {
        assertThat(RecordsSelection.toggle(setOf("a"), "a")).isEmpty()
        assertThat(TransactionsUiState(selectedIds = emptySet()).selectionMode).isFalse()
        assertThat(TransactionsUiState(selectedIds = setOf("a")).selectionMode).isTrue()
    }

    @Test
    fun selectAll_reachesOnlyTheVisiblePostFilterRows() {
        // The rows outside the viewed month / filtered out are simply not in `visibleIds`, so
        // there is nothing for select-all to reach — the bound is structural, not a check.
        val visible = listOf("a", "b", "c")

        assertThat(RecordsSelection.toggleAll(emptySet(), visible)).containsExactly("a", "b", "c")
    }

    @Test
    fun selectAll_onAPartialSelection_completesItRatherThanClearing() {
        assertThat(RecordsSelection.toggleAll(setOf("b"), listOf("a", "b", "c")))
            .containsExactly("a", "b", "c")
    }

    @Test
    fun selectAll_onAFullSelection_clearsAndExits() {
        assertThat(RecordsSelection.toggleAll(setOf("a", "b"), listOf("a", "b"))).isEmpty()
    }

    @Test
    fun selectAll_onAnEmptyMonth_isANoOp() {
        assertThat(RecordsSelection.toggleAll(emptySet(), emptyList())).isEmpty()
    }

    @Test
    fun allVisibleSelected_drivesTheSelectAllAffordance() {
        val state = TransactionsUiState(visibleIds = listOf("a", "b"))
        assertThat(state.copy(selectedIds = setOf("a")).allVisibleSelected).isFalse()
        assertThat(state.copy(selectedIds = setOf("a", "b")).allVisibleSelected).isTrue()
        // An empty month must not read as "everything is already selected".
        assertThat(TransactionsUiState(selectedIds = emptySet()).allVisibleSelected).isFalse()
    }
}
