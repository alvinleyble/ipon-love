package com.iponlove.app.feature.tutorial.presentation

import com.iponlove.app.core.ui.CoachMarkStep

/**
 * Stable target keys shared between the app shell (which tags each element with
 * `Modifier.coachMarkTarget`) and this script. These are the Ipon-specific knowledge the generic
 * `core/ui` engine deliberately doesn't hold (ADR-0034 decision 4).
 */
object TutorialTargets {
    const val PINS = "tutorial.pins"
    const val ADD = "tutorial.add"
    const val MORE = "tutorial.more"
}

/**
 * A single tour step: the visible tooltip plus how it advances. [advancesOnTap] steps wait for the
 * user to actually operate the highlighted target (observed by the shell) instead of showing a
 * "Next" button — teaching real muscle memory (ADR-0034 decision 3).
 */
data class TutorialStep(
    val coachMark: CoachMarkStep,
    val advancesOnTap: Boolean,
)

/**
 * The first-run walkthrough script (ADR-0034 decision 1): bottom-nav shortcuts → the center ⊕ Add
 * button → the More sheet (which also houses navbar customization). Kept intentionally short.
 */
object TutorialScript {
    val steps: List<TutorialStep> = listOf(
        TutorialStep(
            coachMark = CoachMarkStep(
                targetKey = TutorialTargets.PINS,
                stepLabel = "1 of 3",
                title = "Your shortcuts",
                text = "This is your navigation bar. Tap a shortcut to jump straight to that section.",
                primaryLabel = "Next",
            ),
            advancesOnTap = false,
        ),
        TutorialStep(
            coachMark = CoachMarkStep(
                targetKey = TutorialTargets.ADD,
                stepLabel = "2 of 3",
                title = "Quick add",
                text = "Tap the + button anytime to record a transaction — it's here on every screen.",
                primaryLabel = "Next",
            ),
            advancesOnTap = false,
        ),
        TutorialStep(
            coachMark = CoachMarkStep(
                targetKey = TutorialTargets.MORE,
                stepLabel = "3 of 3",
                title = "Find everything else",
                text = "Tap More to reach every other section — and \"Edit navbar\" in there lets you " +
                    "customize which shortcuts sit on your bar.",
                // No button: this step completes when the user really opens the More sheet.
                primaryLabel = null,
            ),
            advancesOnTap = true,
        ),
    )

    val lastIndex: Int get() = steps.lastIndex

    fun stepAt(index: Int): TutorialStep? = steps.getOrNull(index)
}
