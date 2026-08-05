package com.iponlove.app.feature.transactions.presentation

/**
 * The selection algebra behind Records' multi-select mode (v1.7.3 Item 7 / ADR-0064) — the app's
 * first multi-select, and per ADR-0064 decision 6 the shape Notes/Recurring/Budgets are expected
 * to follow if they add one.
 *
 * Selection mode is not a separate flag: it *is* a non-empty selection. That's what makes
 * "deselecting the last row exits" fall out for free rather than needing its own rule.
 *
 * Kept pure and off the ViewModel so the rules are unit-testable without Hilt.
 */
internal object RecordsSelection {

    /** Long-press: enters selection mode with the pressed row already ticked. */
    fun begin(id: String): Set<String> = setOf(id)

    /** Tap inside selection mode. Untickng the last row yields the empty set — i.e. exits. */
    fun toggle(current: Set<String>, id: String): Set<String> =
        if (id in current) current - id else current + id

    /**
     * Select-all, scoped strictly to [visibleIds] — the viewed month's post-filter rows, never all
     * history (ADR-0064 decision 6: the blast radius is capped at what the user can actually see).
     * Toggles, so a second tap on a fully-ticked list clears the selection and exits.
     */
    fun toggleAll(current: Set<String>, visibleIds: List<String>): Set<String> =
        if (visibleIds.isNotEmpty() && current.containsAll(visibleIds)) current - visibleIds.toSet()
        else current + visibleIds
}
