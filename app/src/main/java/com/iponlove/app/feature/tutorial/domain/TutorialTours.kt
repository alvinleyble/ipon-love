package com.iponlove.app.feature.tutorial.domain

/**
 * The stable IDs of every coach-mark tour (ADR-0038). Lives in `domain` (not `presentation`) so the
 * data layer's seen-set migration and any feature screen that fires a tour can reference an ID
 * without depending on the presentation script. Adding a module tour is one constant here plus its
 * step list in `TutorialScript` — no new DataStore key (gating is a single `Set<String>`).
 *
 * Solo tours fire on first visit to their screen (the [SHELL] tour up-front at app-shell mount).
 * Couple tours in [PAIRED_TOURS] additionally require the user to be paired — an unpaired first
 * visit leaves them unseen so they fire later, once paired. [COUPLE_BRIDGE] is special: it isn't a
 * screen tour but a one-shot coach mark fired at the pairing-success moment.
 */
object TutorialTours {
    // Solo
    const val SHELL = "shell"
    // The original RECORDS tour was deleted in v1.7.1 Item 20 (Item 17 removed the ⋮ overflow both
    // of its steps were anchored to) and Records went tour-less under ADR-0059. Item 2 Slice 3
    // reopens that: the FAB wheel scrolling to switch its armed action is exactly the "not
    // inferable from looking at it" bar ADR-0059 sets, per ADR-0062 decision 3. Deliberately a new
    // id, not a reuse of the retired "records" — a seen-set that already contains the old, deleted
    // tour's id must NOT suppress this unrelated one.
    const val RECORDS_FAB_WHEEL = "records_fab_wheel"
    const val RECURRING = "recurring"
    const val ANALYSIS = "analysis"
    const val MANAGE = "manage"
    const val SAVINGS = "savings"
    const val NOTES = "notes"
    const val TRANSACTION_ENTRY = "transaction_entry"

    // Couple (paired-gated)
    const val COUPLE = "couple"
    const val NOTES_COUPLE = "notes_couple"
    const val SAVINGS_COUPLE = "savings_couple"
    const val COUPLE_SETTINGS = "couple_settings"
    const val TRANSACTION_ENTRY_COUPLE = "transaction_entry_couple"

    /** One-shot bridge fired at pairing success, not on any screen visit. */
    const val COUPLE_BRIDGE = "couple_bridge"

    /** Tours that only fire while paired; an unpaired first visit leaves them unseen. */
    val PAIRED_TOURS: Set<String> = setOf(
        COUPLE, NOTES_COUPLE, SAVINGS_COUPLE, COUPLE_SETTINGS, TRANSACTION_ENTRY_COUPLE, COUPLE_BRIDGE,
    )
}
