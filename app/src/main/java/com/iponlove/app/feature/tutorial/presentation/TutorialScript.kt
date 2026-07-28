package com.iponlove.app.feature.tutorial.presentation

import com.iponlove.app.feature.tutorial.domain.TutorialTours

/**
 * Stable target keys shared between the screens (which tag each element with
 * `Modifier.coachMarkTarget`) and this script. These are the Ipon-specific knowledge the generic
 * `core/ui` engine deliberately doesn't hold (ADR-0034 dec. 4).
 *
 * A tour's steps may reuse one anchor across all of its steps — the tooltip just re-reads the same
 * bounds with new text. That's deliberate: a single reliably-laid-out anchor (a FAB, a tab row) is
 * far more robust than chasing a different element per step, which risks a step whose target isn't
 * on-screen when the tour starts (ADR-0038 "first-step target must be visible" consequence).
 */
object TutorialTargets {
    // Shell (bottom bar)
    const val PINS = "tutorial.pins"
    const val ADD = "tutorial.add"
    const val MORE = "tutorial.more"

    // Solo module anchors
    const val RECURRING_CALENDAR = "tutorial.recurring.calendar"
    const val RECURRING_ADD = "tutorial.recurring.add"
    const val ANALYSIS_PERIOD = "tutorial.analysis.period"
    const val ANALYSIS_TABS = "tutorial.analysis.tabs"
    const val MANAGE_TABS = "tutorial.manage.tabs"
    const val MANAGE_ADD = "tutorial.manage.add"
    const val SAVINGS_ADD = "tutorial.savings.add"
    const val NOTES_ADD = "tutorial.notes.add"
    const val TXN_TYPE = "tutorial.txn.type"

    /**
     * The privacy eye. Anchored once, inside the shared `PrivacyEyeAction`, so it resolves on every
     * header that uses it (Analysis, Manage, Recurring, Couple) — only the Analysis tour has a step
     * for it (ADR-0059: the eye is a global control that isn't inferable from looking at it).
     */
    const val PRIVACY_EYE = "tutorial.privacy.eye"

    // Couple anchors
    const val COUPLE_TABS = "tutorial.couple.tabs"
    const val SETTINGS_COUPLE = "tutorial.settings.couple"
}

/** When a step applies. Drives the runtime-filtered step list (and thus the "N of M" label). */
enum class StepCondition {
    ALWAYS,
    /** Shown only before pairing — e.g. the "a future partner won't see private entries" hint. */
    UNPAIRED_ONLY,
    ;

    fun matches(paired: Boolean): Boolean = when (this) {
        ALWAYS -> true
        UNPAIRED_ONLY -> !paired
    }
}

/**
 * A single tour step: which target to highlight, the copy, how it advances, and when it applies.
 * [advancesOnTap] steps wait for the user to really operate the highlighted target (observed by the
 * shell) instead of showing a "Next" button — teaching real muscle memory (ADR-0034 dec. 3).
 */
data class TutorialStep(
    val targetKey: String,
    val title: String,
    val text: String,
    val advancesOnTap: Boolean = false,
    val condition: StepCondition = StepCondition.ALWAYS,
)

/**
 * The onboarding registry (ADR-0038 dec. 1): a `tourId → steps` map, each tour 2–3 feature-oriented
 * steps. Solo tours fire on first visit to their screen; the shell tour up-front at app-shell mount.
 * Couple tours (in [TutorialTours.PAIRED_TOURS]) fire on first visit once paired. Progress labels
 * ("N of M") and the primary-button label are computed at runtime from the pairing-filtered list —
 * they are not baked into these steps (see [TutorialViewModel]).
 */
object TutorialScript {

    private val tours: Map<String, List<TutorialStep>> = mapOf(
        TutorialTours.SHELL to listOf(
            TutorialStep(
                targetKey = TutorialTargets.PINS,
                title = "Your shortcuts",
                text = "This is your navigation bar. Tap a shortcut to jump straight to that section.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.ADD,
                title = "Quick add",
                text = "Tap the + button anytime to record a transaction — it's here on every screen.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.MORE,
                title = "Find everything else",
                // Deliberately one longer bubble rather than a fourth step: a coach mark can't render
                // over the More sheet (it's a ModalBottomSheet with its own window), and this step's
                // advancesOnTap ends the tour the moment the sheet opens. The "right on top of
                // whatever you're doing" clause is load-bearing — it's the only thing telling the
                // user the Calculator tap won't navigate (ADR-0059).
                text = "Tap More to reach every other section. Calculator opens right on top of " +
                    "whatever you're doing, and \"Edit navbar\" lets you customize your shortcuts.",
                advancesOnTap = true,
            ),
        ),

        // The Records tour was retired in v1.7.1 Item 17 (its two steps were both anchored to the ⋮
        // overflow's "Recurring rules" entry, which Item 17 removed as redundant once Recurring
        // became its own top-level module) and its now-dead constant deleted in Item 20. Records
        // having no tour is correct by design, not a gap — ADR-0059.

        TutorialTours.RECURRING to listOf(
            TutorialStep(
                targetKey = TutorialTargets.RECURRING_CALENDAR,
                title = "What's coming up",
                // Deliberately NOT conditioned on entitlement: once enforcement flips this advertises
                // a locked tab, and the blurred preview + upsell it lands on IS the sell
                // (subscription-paywall-design §8.2). StepCondition stays about pairing (ADR-0059).
                text = "Switch to the Calendar tab to see every recurring rule laid out by date.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.RECURRING_ADD,
                title = "Add a rule",
                text = "Tap + to create a recurring rule. You can pause it or skip a single " +
                    "occurrence later from its menu.",
            ),
        ),

        TutorialTours.ANALYSIS to listOf(
            TutorialStep(
                targetKey = TutorialTargets.ANALYSIS_PERIOD,
                title = "Pick a period",
                text = "Step through months here, or pick a longer stretch — quarter, year, all-time.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.ANALYSIS_TABS,
                title = "Three ways to look",
                text = "Switch between the Donut (by category), Flow (income vs. spending) and " +
                    "Calendar views.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.PRIVACY_EYE,
                title = "Hide your amounts",
                // "not just on this screen" is the entire reason this step exists — the eye toggles
                // the GLOBAL privacy flag, never a local reveal. Do not trim that clause. It lives on
                // Analysis (not Manage or Shell) because Analysis shows the most amounts, and because
                // Shell's landing screen isn't guaranteed to be one that has this anchor.
                text = "Tap the eye to mask every amount in the app — not just on this screen. " +
                    "Handy when someone's looking over your shoulder.",
            ),
        ),

        TutorialTours.MANAGE to listOf(
            TutorialStep(
                targetKey = TutorialTargets.MANAGE_TABS,
                title = "Accounts, Categories, Budgets",
                text = "These three tabs are where you set up the building blocks of your money.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.MANAGE_ADD,
                title = "Add one",
                text = "Tap + to add whatever the current tab shows — a new account, category or budget.",
            ),
        ),

        TutorialTours.SAVINGS to listOf(
            TutorialStep(
                targetKey = TutorialTargets.SAVINGS_ADD,
                title = "Set a goal",
                text = "Tap + to create a savings goal with a target amount to work toward.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.SAVINGS_ADD,
                title = "Track progress",
                text = "Open a goal to log contributions and watch the progress bar fill up.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.SAVINGS_ADD,
                title = "Wrap it up",
                text = "Reached a goal? Archive it to keep your list focused on what's still active.",
            ),
        ),

        TutorialTours.NOTES to listOf(
            TutorialStep(
                targetKey = TutorialTargets.NOTES_ADD,
                title = "Create a note",
                text = "Tap + to start a note — handy for anything money-adjacent you want to remember.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.NOTES_ADD,
                title = "More than plain text",
                text = "The editor supports rich text, checklists and images.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.NOTES_ADD,
                title = "Shareable later",
                text = "Once you pair with a partner, any note can be shared with them.",
            ),
        ),

        TutorialTours.TRANSACTION_ENTRY to listOf(
            TutorialStep(
                targetKey = TutorialTargets.TXN_TYPE,
                title = "Type of transaction",
                text = "Start by picking Income, Expense or Transfer — the rest of the form adapts.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.TXN_TYPE,
                title = "Private entries",
                text = "Lower down, the Private toggle keeps an entry to yourself. When you pair with " +
                    "a partner later, private transactions stay out of their combined view.",
                condition = StepCondition.UNPAIRED_ONLY,
            ),
        ),

        // Couple (paired-gated) — fire lazily on first visit once paired.
        TutorialTours.COUPLE to listOf(
            TutorialStep(
                targetKey = TutorialTargets.COUPLE_TABS,
                title = "Your combined view",
                text = "Spending shows the money you and your partner share, side by side.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.COUPLE_TABS,
                title = "Who owes whom",
                text = "The Debts tab tracks IOUs between the two of you and helps you settle up.",
            ),
        ),

        TutorialTours.NOTES_COUPLE to listOf(
            TutorialStep(
                targetKey = TutorialTargets.NOTES_ADD,
                title = "Shared notes",
                text = "Now that you're paired, open any note and toggle sharing in the editor to let " +
                    "your partner see and edit it.",
            ),
        ),

        TutorialTours.SAVINGS_COUPLE to listOf(
            TutorialStep(
                targetKey = TutorialTargets.SAVINGS_ADD,
                title = "Shared goals",
                text = "Paired up, a savings goal can be shared so you both contribute toward it.",
            ),
        ),

        TutorialTours.COUPLE_SETTINGS to listOf(
            TutorialStep(
                targetKey = TutorialTargets.SETTINGS_COUPLE,
                title = "Manage your couple",
                text = "Your partner, your accent color in the combined view, and unpairing all live here.",
            ),
        ),

        TutorialTours.TRANSACTION_ENTRY_COUPLE to listOf(
            TutorialStep(
                targetKey = TutorialTargets.TXN_TYPE,
                title = "Paid for your partner",
                text = "On an expense you can flag \"Paid for partner\" to record it as a debt they owe you.",
            ),
            TutorialStep(
                targetKey = TutorialTargets.TXN_TYPE,
                title = "Keep it private",
                text = "The Private toggle keeps an entry off your partner's combined view.",
            ),
        ),

        TutorialTours.COUPLE_BRIDGE to listOf(
            TutorialStep(
                targetKey = TutorialTargets.MORE,
                title = "You're paired! 🎉",
                text = "Couple features just unlocked. Tap More to open your Spending view and explore " +
                    "everything you now share.",
            ),
        ),
    )

    /** The raw (unfiltered) steps for [tourId], or empty if there is no such tour. */
    fun stepsFor(tourId: String): List<TutorialStep> = tours[tourId].orEmpty()
}
