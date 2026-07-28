# Coach-mark coverage is discoverability triage, not per-module completeness

## Context

[ADR-0038](0038-per-module-lazy-coach-mark-tours.md) settled *how* tours fire: a `tourId → steps` registry, lazily on first visit, gated by a `Set<String>` of seen IDs. It named a taxonomy of 12 tours covering essentially every module of the app as it stood in v1.6.2 (2026-07-05). It never stated *what earns a step* — coverage was implicit in the taxonomy, and the taxonomy happened to be near-total.

That implicit answer stopped scaling. By v1.7.1 the app has grown past the shape 0038 described:

- **Modules gained a tour-less peer.** `TutorialTours.RECORDS` was retired in v1.7.1 Item 17 (both its steps anchored to an overflow entry that item deleted), on Alvin's explicit call of *no replacement*. Records — the most-used module — now has no first-visit tour. `SETTINGS` never had one (only `COUPLE_SETTINGS`, on Personalize). `CALCULATOR` has none and structurally **cannot** have one: it is an [overlay module](0058-calculator-overlay-module.md) with `navigable = false` and no screen to host a `StartTourOnFirstVisit`.
- **Cross-cutting controls appeared that belong to no module.** The notification bell (v1.7.1 Item 6) renders on six module headers; the privacy eye became a shared header action (Item 16) that toggles a **global** flag from any screen; the calculator bubble spawns over whatever screen you're on.

Read as a completeness mandate, ADR-0038 turns each of these into a defect, and every future module into a copy-writing obligation. The v1.7.1 Item 20 audit forced the question directly, and Alvin chose the other reading (2026-07-28).

Two findings from that audit constrain any answer:

1. **Steps already routinely describe things their anchor isn't.** `SAVINGS` and `NOTES` each run three steps on a single anchor (their FAB), covering creating, tracking and archiving. ADR-0038's own script comment defends this: one reliably-laid-out anchor beats chasing a different element per step. So "this control has no good anchor" is a weak argument against a step — the anchor and the subject need not coincide.
2. **The coach-mark overlay cannot draw over a modal sheet.** `CoachMarkOverlay` lives inside `IponApp`'s Scaffold `Box`; the More menu is a `ModalBottomSheet` owning its own window. This is the same z-order fact [ADR-0058](0058-calculator-overlay-module.md) finding 1 relies on, seen from the other side: anything in that `Box` is structurally incapable of covering a sheet. The `SHELL` tour's last step is `advancesOnTap` on `MORE`, so the tour also *ends* the instant the sheet opens.

## Decision

**A coach-mark step exists only where the interaction is not inferable from looking at the screen. Absence of a tour is a valid, deliberate state.**

### 1. The triage test

Before adding a step, ask: *can a competent first-time user work this out by looking at it?* If yes, no step — regardless of how new, prominent, or hard-won the feature is. Applied to the v1.7.1 surface:

| Surface | Earns a step? | Why |
|---|---|---|
| Calculator bubble | **Yes** | Tapping a navbar/More entry that *doesn't navigate* is the least guessable interaction in the app |
| Global privacy eye | **Yes** | Its effect is invisible on the screen you tap it from — masking Records from the Analysis header is unguessable |
| Notification bell | **No** | A bell with a badge is a universal idiom |
| Records filter, Export in Settings, balance adjustment, debt-overpay cascade, couple photo | **No** | Discoverable in place |
| Records, Settings, Calculator having no tour | **Correct** | Not gaps — nothing in them fails the test |

This supersedes the coverage implied by ADR-0038's 12-tour taxonomy. **ADR-0038's mechanism is untouched** — lazy per-module firing, the seen-set gate, the active-tour guard, `PAIRED_TOURS`, and "describe in place, never navigate" all stand exactly as written. This ADR answers only the question 0038 left open.

Rejected: **per-module completeness** (every module gets a tour, for symmetry, so "Replay tutorial" reads as a product tour). It coach-marks a new user on nearly every screen they open in their first week, and it makes every future module a copy obligation on an app that is still growing. Symmetry is not a user benefit.

### 2. `StepCondition` stays about pairing, never entitlement

`StepCondition` gates steps on structural facts about the *account* — today only `ALWAYS` / `UNPAIRED_ONLY`. It must not learn about premium entitlement.

The live case: the `RECURRING` tour's first step points at the Calendar tab, which is `Feature.RECURRING_CALENDAR` — paywalled, individual scope, currently dormant ([ADR-0044](0044-entitlement-client-trusted-advisory-column.md)). When enforcement flips, that step will tell a free user to go use something they don't have.

**That is correct and intended.** Tapping it lands on the existing blurred preview + upsell, which [subscription-paywall-design.md](../build/subscription-paywall-design.md) §8.2 designates as *the sell*. The step becomes a free upsell impression, not a bug.

The alternative — a `PREMIUM_ONLY` / `FREE_ONLY` condition — was rejected because it wires entitlement into the tutorial: `TutorialViewModel` would observe `premiumGate`, and the runtime "N of M" label (ADR-0038 consequence 1) would become entitlement-dependent. A real architectural coupling, for a user-facing outcome the paywall design already wants.

### 3. Steps whose subject lives inside a modal sheet are not buildable

Given finding 2, a coach mark can anchor to the **More button** but never to anything **inside** the More sheet. Any proposed step whose subject only exists inside a sheet must either be folded into the copy of a step anchored outside the sheet, or booked as an engine change (hoisting `CoachMarkOverlay` to a top-level layer) — never smuggled in as script data.

This is why Calculator is taught as a clause on the existing `SHELL` "More" step rather than its own spotlight on the Calculator row.

## Consequences

- **"Why doesn't module X have a tour?" now has a written answer.** Future audits check surfaces against the triage test rather than against a module list.
- **The tour count will drift below the module count, permanently.** Expected, not decay.
- **Cross-cutting controls get adopted by a thematically-appropriate tour, not their own.** The privacy eye becomes a third `ANALYSIS` step (the screen showing the most amounts) with its anchor added inside the shared `PrivacyEyeAction` — one `modifier` argument covering Analysis, Manage, Recurring and Couple at once. Records is not covered: it still reimplements the eye inline (an Item 16 leftover, booked separately as v1.7.1 Item 22).
- **The `SHELL` tour's "More" step accumulates.** It is now the only place anything reachable-only-through-the-sheet can be taught, so its copy will keep growing. When it stops reading as one thought, that is the trigger to book the overlay-hoisting engine change — not a reason to add a fourth shell step.
- **`TutorialTours.RECORDS` is deleted.** It was referenced by nothing but tests, and `seenTour_doesNotStart` asserted the seen-set gate using a tour with **zero steps** — a test that would still pass with the gate removed. The orphan `"records"` string in existing users' seen-set DataStore needs no migration: the gate is a `Set<String>` and an unknown member is never consulted.
