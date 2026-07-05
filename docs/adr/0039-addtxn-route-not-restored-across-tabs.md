# Add/Edit-transaction stay top-level nav routes; tab-switching drops them unsaved to stop cross-tab resurrection

## Context

Alvin reported (v1.6.2 Item 2) that the "new transaction" screen intermittently reappears on top of a bottom-nav tab (Records, Analysis) even after tapping into that tab — "most of the time," with no obvious trigger.

Investigation corrected two assumptions in the report and in ADR-0038:

- **It is not a bottom sheet.** `AddTransactionScreen` is a full-screen `Scaffold` (`feature/transactions/presentation/AddTransactionScreen.kt`), reached via a **top-level nav route** `ADD_TRANSACTION_ROUTE` declared in `IponApp.kt` *outside every module graph* (as ADR-0033 dec. 2 intended, so it never inherits a tab's reset-on-retap). `EDIT_TRANSACTION_ROUTE` is structurally identical — the same root-level composable, reached from the Records list. (ADR-0038's "ADD_TRANSACTION bottom-sheet modal" wording is inaccurate; it is a route, not a `ModalBottomSheet`.)
- **Visibility is 100% nav back-stack state**, not a stray UI boolean. The only shell-level boolean is `showMore` for the More sheet. So the doc's "stale sheet-open flag" hypothesis was ruled out.

**Reproduced deterministically** on an emulator (Medium_Phone, stagingDebug):

> Open Add from a tab → switch to a **different** tab → switch **back** to the tab Add was opened from → Add reappears on top of that tab (with no tab highlighted). Back from the resurrected screen returns cleanly to the tab.

Because users open ⊕ Add from whatever tab they are on, bounce elsewhere, and come back, the deterministic sequence *presents* as random. The bug was introduced/exposed by the nested-nav-graph rework (`f4ae4c8`, ADR-0033), which added the standard bottom-nav save/restore machinery in `switchTab`:

```kotlin
navigate(dest.graphRoute()) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

That pattern assumes every reachable destination lives inside a tab graph. `ADD_TRANSACTION_ROUTE` does not: it is a root-level sibling of the graphs, so when it sits on the back stack at the moment of a tab switch, `popUpTo(rootStart) { saveState = true }` sweeps it into the origin tab's saved back stack, and a later `restoreState = true` reinstates it when the user returns to that tab.

## Decision

**Keep Add/Edit as top-level nav routes (ADR-0033 dec. 2 unchanged), and make `switchTab` drop any non-tab root route off the back stack *unsaved* before it navigates.**

1. **In `switchTab`, if the current destination is not inside any module graph** (i.e. it is a root-level route — Add or Edit today), `popBackStack()` it first, without `saveState`. Then run the normal tab navigate. A route that was never saved cannot be restored, so the resurrection cannot happen.

2. **Detection keys off "not in any module graph"** (iterate `NavRegistry.all` and check `currentDestination.hierarchy`), **not** an explicit `route == ADD/EDIT` match. The NavHost has exactly two root-level composables — Add and Edit — and every other destination lives in a `navigation(){}` graph, so the general check covers both today and any future root route, with no risk of over-triggering.

3. **A single `popBackStack()` suffices.** You cannot stack two root routes without passing through a tab, so at most one non-tab route is ever on top. The pop returns to whatever tab/sub-screen Add was opened from; the subsequent `popUpTo(rootStart){saveState}` then saves that tab's stack *without* Add. Deep sub-screens are preserved: Records → Recurring → Add pops back to Recurring and saves Recurring, not Add.

4. **Rejected: convert Add into a shell-level boolean modal** (like `showMore`), which would put it physically outside the back stack. It kills the bug class by construction, but Edit stays a route and would still need this fix (or its own modal), and Add's full-screen `Scaffold` would need rehosting — more blast radius for a bug the surgical fix already closes for both Add and Edit uniformly. It also amends ADR-0033 dec. 2 for no additional correctness. Revisit only if Add is redesigned into an actual bottom sheet for other reasons.

## Consequences

- The fix is ~5 lines, local to `switchTab`; ADR-0033 dec. 2 (Add/Edit as isolated top-level routes) is untouched.
- Add and Edit are both fixed by the same change — no per-route handling.
- Possible one-frame flash of the origin tab between the `popBackStack` and the `navigate`; not observed in practice (both run synchronously in the tab-click callback).
- **Verified by running, not by a unit test** (per CLAUDE.md: nav/UI is verified on-device; the JVM suite stays seconds-fast). This nav back-stack behavior is not reachable from a JVM unit test, and an instrumented `TestNavHostController` test was judged not worth the emulator-only scaffolding for a 5-line fix. Re-run the emulator repro after the fix: Add no longer resurrects on tab-return, Edit likewise, re-tap-active-tab still pops to root, and deep sub-screens (Records → Recurring) stay preserved.

## Suggested build

Sonnet, low effort — the design is fully specified and the change is a single local edit to `switchTab` in `IponApp.kt`.
