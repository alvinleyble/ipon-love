package com.iponlove.app.feature.recurring.presentation

import com.iponlove.app.feature.recurring.domain.model.UpcomingOccurrence

/**
 * State for the Records "Coming up" preview (Item 37, Slice 2) — the premium forecast layer's
 * forward glance at scheduled income + bills in a short window.
 *
 * [items] is the live, derived list of future occurrences (oldest-first, capped for a preview).
 * [locked] is the `RECURRING_FORECAST` gate: enforcement ON without access. The card renders as
 *  - nothing, when there's nothing upcoming (both tiers self-hide on an empty window);
 *  - the full preview, when unlocked (the dormant/premium path — byte-identical pre-flip);
 *  - a compact upsell teaser (item count only, no amounts), when locked.
 *
 * [collapsed] (Item 19) only hides the occurrence rows — the header (with count) always renders
 * so the card stays discoverable and re-expandable. Persisted, not `remember`-only; ignored by
 * the locked teaser, which has nothing to collapse.
 */
data class ComingUpUiState(
    val items: List<UpcomingOccurrence> = emptyList(),
    val locked: Boolean = false,
    val collapsed: Boolean = false,
)
