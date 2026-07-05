# Onboarding v2 is a registry of per-module lazy coach-mark tours, gated by a set of seen tour IDs

## Context

The first-run tutorial shipped in v1.6.1 (ADR-0034) is a single 3-step linear tour — bottom-nav pins → ⊕ Add → More sheet — driven by `TutorialScript` (a flat `List<TutorialStep>`), gated by one local-only DataStore boolean (`tutorial_seen`), with all targets tagged in the app shell (`IponApp.kt`). Alvin found it "very underwhelming": it points at nav destinations but never teaches a user how to actually *use* each module (the Analysis tabs/steppers, the Records recurring calendar, transaction types, where to add accounts/categories/budgets, Savings, Notes). It also says nothing about couple features, which don't exist or apply until the user pairs.

The engine underneath is already generic and reusable: `core/ui/CoachMark.kt` (`CoachMarkState` + `CoachMarkOverlay` + `Modifier.coachMarkTarget`) is a feature-agnostic anchored-tooltip primitive that anchors a tooltip to any target laid out under the same coordinate root, never intercepts touches, and advances either by a "Next" button or by observing the real target being operated (ADR-0034 dec. 3–4). This item extends that engine to many tours; it does not redesign it.

The app's real screen structure (confirmed in `IponApp.kt`/`NavRegistry`): `RECORDS`, `RECURRING`, `NOTES`, `ANALYSIS`, `MANAGE` (Accounts/Categories/Budgets in one screen), `COUPLE` (Combined | Debts tabs in one screen), `SAVINGS`, `SETTINGS`, plus the `ADD_TRANSACTION` bottom-sheet modal.

## Decision

**Generalize the one linear tour into a registry of per-module tours, each firing lazily on first visit to its screen, gated by a set of seen tour IDs.**

1. **Per-module lazy mini-tours.** `TutorialScript` becomes a `tourId → List<TutorialStep>` registry. Each tour is **2–3 feature-oriented steps** (the depth benchmark for every tour, so none drags). A module tour fires the first time the user lands on that screen. The existing **shell tour stays up-front** at app-shell mount — it teaches navigation itself, which the user needs before visiting any module.

2. **Gating = a `Set<String>` of seen tour IDs** in local DataStore, replacing the single `tutorial_seen` boolean. A tour fires when its ID is absent from the set. One key, extensible — a new module adds no new preference key. **Legacy migration:** on first read, if the old `tutorial_seen == true` and the set is unset, seed the set with the shell tour ID so existing testers don't re-see the shell tour (all new module tours still fire).

3. **Centralized architecture.** One `TutorialViewModel` + one `CoachMarkOverlay` stay at the shell. The shell's `CoachMarkState` is shared down to feature screens via a **CompositionLocal**; each screen tags its own targets with `coachMarkTarget` and calls `vm.maybeStartTour("analysis")` in a `LaunchedEffect` on first visit. An **active-tour guard** ensures only one tour runs at a time — `maybeStartTour` no-ops if a tour is already active (the un-started tour stays unseen and fires on a later first-visit).

4. **Multi-surface screens: describe in place, never navigate** (upholds ADR-0034 dec. 3). Analysis points at the tab row and names donut/flow/calendar in one step; the Couple screen points at the Combined | Debts tab row. No engine-driven tab switching or navigation — a coach mark only anchors to targets currently laid out.

5. **Skip = this tour only** (marks just that ID seen; other modules still fire). **One "Replay tutorial"** in Settings clears the entire set and restarts the shell tour; module tours re-fire as the user revisits screens. (Rejected: a per-tour replay list — over-engineered for V1.)

6. **Transaction-entry tour** fires on first open of the Add sheet, teaching transaction types. **The Private-flag disclaimer lives in two places:** a conditional tour step shown only when the user is unpaired, *and* a persistent inline hint on the sheet whenever Private is toggled while unpaired (durable and contextual, since a user may toggle Private long after onboarding).

7. **Post-pairing = lazy couple tours mirroring the solo model, gated on `isPaired`.** Pairing unlocks couple-scoped tour IDs that fire on first visit to each couple surface — teaching each in context rather than in one abstract tour fired before the user is on any of those screens. Couple surfaces that are additions to already-toured solo screens (Notes sharing, shared Savings, the Paid-for-partner flag) are **distinct tour IDs** so first-visit re-fires them post-pairing. **One bridging coach mark** fires at the pairing-success moment, anchored to a currently-visible entry point (the More button or a success-screen CTA — the Couple destination may live in the More sheet, not a navbar pin), pointing the user toward the combined view.

**Tour taxonomy (12 tours).** Solo: `shell`, `records`, `recurring`, `analysis`, `manage`, `savings`, `notes`, `transaction_entry`. Couple (paired-gated): `couple` (Combined | Debts tab row), `notes_couple`, `savings_couple`, `couple_settings`, `transaction_entry_couple`. Debts is a *tab within* the Couple screen, so it is a step in the `couple` tour, not its own tour.

## Consequences

- **Conditional steps require a runtime-computed progress label.** The Private-flag step is dropped when paired, so "N of M" must be computed from the runtime-filtered step list rather than hardcoded (today's `"1 of 3"` string won't survive).
- **Each tour's first-step target must be laid out and visible when the tour starts.** `CoachMarkOverlay` draws nothing if the target isn't positioned yet; screens with scroll/tab state need their first target on-screen at tour start.
- **Couple-tour gating needs a pairing-status source** injected into `TutorialViewModel` (observe the couple repository / a get-pairing-status use case).
- **Couple tour IDs, once seen, stay seen across unpair/re-pair** — an accepted trade-off; the user already learned those surfaces.
- **The gate stays local-only and unsynced** (as ADR-0034), so it re-arms on a fresh install / cleared local storage, still covering both trigger cases.

## Rejected

- **One long linear tour up front** (the doc's original framing, both solo and post-pairing) — teaching a screen before the user is on it is abstract and forgettable, forces the engine to drive cross-screen navigation to walk one tour, and can't anchor tooltips to screens that aren't currently shown. Lazy per-module tours teach in context and keep each script small.
- **A boolean preference key per module** — greppable but adds a key + migration thought per new module; a single `Set<String>` is extensible with zero new keys.
- **Auto-switching tabs to demo each surface** — richest demo but breaks ADR-0034 dec. 3 (never drive navigation) and couples the engine to each screen's tab state.
- **Skip suppresses all onboarding** — rejected in favor of per-tour skip for maximum coverage; the single "Replay tutorial" is the escape hatch for a user who wants everything again.
