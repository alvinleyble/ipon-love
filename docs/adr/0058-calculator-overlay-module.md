# Calculator becomes an overlay module: a persistent in-app bubble, not a navigable screen

## Context

Calculator has shipped since V1.5 as an ordinary top-level module: a registry entry ([NavDestination.kt:61](../../app/src/main/java/com/iponlove/app/navigation/NavDestination.kt#L61)), its own single-node nav graph ([IponApp.kt:398-402](../../app/src/main/java/com/iponlove/app/navigation/IponApp.kt#L398-L402)), and a full-screen `CalculatorScreen` reached via `switchTab`. Opening it therefore *replaces* whatever tab you were on.

Alvin's framing (2026-07-28): *"users might be calculating something from records and would have to go back and forth to remember the numbers."* Read a figure off Records → switch to Calculator → the figure is gone → switch back to read the next one. The cost isn't the taps, it's holding numbers in your head across a full screen swap.

He scoped the ask in the same breath: the bubble is **in-app only**, never a `SYSTEM_ALERT_WINDOW` draw-over-other-apps bubble, so no new runtime permission is in play.

Three codebase findings shaped the design before any decision was made:

1. **The app-lock z-order question doesn't exist.** `LockScreen` renders in its own platform `Dialog` window, deliberately — [MainActivity.kt:296-314](../../app/src/main/java/com/iponlove/app/MainActivity.kt#L296-L314) documents that a same-window `Box` overlay was rejected because any open dialog would draw above it. Anything inside `IponApp`'s root `Box` is therefore *structurally incapable* of covering the lock. The same is true of the More `ModalBottomSheet` and every in-screen dialog: they own their windows and win for free.
2. **The calculator has no state to migrate.** `CalculatorState` is an immutable data class driven by a pure `object CalculatorEngine`, held in a single `remember` ([CalculatorScreen.kt:52](../../app/src/main/java/com/iponlove/app/feature/calculator/presentation/CalculatorScreen.kt#L52)). `CalculatorViewModel` holds *only* the premium lock flow. Hoisting is one line, and `CalculatorEngine` is untouched.
3. **There is a live crash trap on the upgrade path.** [IponApp.kt:180](../../app/src/main/java/com/iponlove/app/navigation/IponApp.kt#L180) restores the last module across a force-stop by feeding a persisted module id in as the `NavHost` **start destination**, guarded by `isKnownModule = NavRegistry.byId::containsKey` ([NavbarViewModel.kt:86](../../app/src/main/java/com/iponlove/app/navigation/NavbarViewModel.kt#L86)). Because Calculator *stays in the registry* under this ADR, that guard still passes — and hands the `NavHost` a graph route that no longer exists. See consequence 1.

## Decision

### 1. Calculator stops being a navigable destination — the registry gains "overlay module"

Tapping Calculator (pinned tab or More sheet row) **spawns the bubble over the current screen**; no navigation occurs. `CalculatorScreen` and its `navigation(CALCULATOR)` graph are **deleted**. `NavDestination` gains a `navigable: Boolean = true` flag, set `false` for Calculator alone — the first registry module with no graph.

The two alternatives were rejected for the same reason: neither removes the round trip.

- **Keep the route, add a "pop out" button** — the user still lands on a full screen first, then has to discover a second affordance to get what they wanted. Two code paths for one feature.
- **Bubble-only but keep the route as an unreachable shell** (purely to preserve the registry's every-module-has-a-graph invariant) — cheapest migration, but ships dead code that rots and leaves the registry claiming Calculator is navigable when it isn't.

Keeping the registry entry (rather than deleting it) is what preserves pinnability, the More sheet row, and the navbar editor listing — the module still *feels* like a module, it just acts on the current screen instead of replacing it.

### 2. State is session-scoped, hoisted into the app shell

Bubble open-state, position, and `CalculatorState` are hoisted into `IponAppContent` as `rememberSaveable`, so the bubble and its running value survive tab switches, rotation, backgrounding and returning, and an app-lock unlock. They die only on **process death** (force-stop / cold start), where the bubble returns closed and cleared.

The booking's phrasing — *"does not survive leaving or backgrounding the app"* — was doing two jobs, and only one of them is a real constraint. Ruling out a system overlay: **kept**. Dying on background: **rejected**, because [ADR-0023](0023-app-lock-overlay.md) deliberately keeps the whole app composed *underneath* the lock precisely so in-progress work survives, and the lock fires after 30s in background. A bubble that self-destructed on background would mean glancing at a text message wipes a half-typed calculation, and unlocking would silently return the user to a screen missing the tool they were using.

Persisting across process death (DataStore) was also rejected: a scratch value outliving a force-stop fights the ephemeral mental model and adds restore-ordering questions against the nav restore for no gain.

### 3. Shape: the collapsed pill carries the number

**Collapsed** is a draggable pill showing the current running value (`1,590`), edge-snapped. **Expanded** is a compact keypad panel anchored to the pill's side. Tap the pill to expand, chevron or pill-tap to collapse.

This is the load-bearing decision, and it follows directly from the stated pain. The classic Messenger idiom — an icon-only bubble — hides the number behind a tap, which trades a screen swap for a tap rather than eliminating the trip. With the value on the pill, the common case never requires expanding at all. An always-expanded panel (no collapsed state) was rejected as a permanent ~40% screen blocker that users would close constantly, losing the value each time.

### 4. Three verbs, and collapse ≠ close

| Gesture | Result |
|---|---|
| Tap Calculator (pin / More) while closed | Opens **expanded**, ready to type |
| Tap Calculator while open | **Closes** and clears |
| ✕ on the panel | **Closes** and clears |
| Back while expanded | **Collapses** to the pill, value kept — never closes, never navigates |
| Back while collapsed | Ordinary app back |
| Tap pill | Expands |

The nav entry is a toggle so there is always an escape hatch reachable from the bar, even if the pill has been dragged somewhere awkward. Back **collapsing rather than closing** is the deliberate deviation from the conventional Android reading: back is the most reflexively pressed control in the app, and users press it while navigating — exactly when a live scratch value is most valuable. Making the single most-pressed button a value-destroyer was rejected.

### 5. Visible everywhere in the signed-in shell; window-owning layers win for free

The bubble persists over **every** route inside `IponApp`, including Add/Edit transaction — the single highest-value moment, where a computed total is about to be entered. Restricting it to top-level modules was rejected precisely because it would hide the bubble at that moment, gutting decision 7.

Ordering, in full:

```
app lock Dialog        ─┐
dialogs / More sheet    ├─ own platform windows → cover the bubble, no code
─────────────────────── ┘
coach-mark overlay      ── drawn above the bubble (explicit, one line)
───────────────────────
BUBBLE
───────────────────────
NavHost: every route, Add/Edit transaction included
```

The coach-mark overlay outranks the bubble because the first-run walkthrough anchors tooltips to specific bottom-bar targets; a bubble floating over a tutorial tooltip is a bad first impression for a saved tap. The bubble does not exist at all outside the authed shell (onboarding, auth, splash render in other branches).

### 6. Under enforcement, a locked user gets the paywall — not a bubble

`Feature.CALCULATOR` remains an individual-scope soft gate ([subscription-paywall-design.md](../build/subscription-paywall-design.md) §8). With the full screen deleted, the lock moves to spawn time: **locked ⇒ tapping Calculator logs the same `upsell_tap` funnel event (source `calculator`) and navigates to the paywall.** No bubble spawns.

`FeatureLockedPanel` is `fillMaxSize()` with 32dp padding and centered content ([FeatureLockedPanel.kt:33-45](../../app/src/main/java/com/iponlove/app/core/ui/FeatureLockedPanel.kt#L33-L45)) — it cannot live inside a ~280dp bubble without a new compact variant, and a shrunken upsell card is a worse pitch than the screen built for it. Hiding Calculator from the registry when locked was rejected outright: nothing in the app hides modules by entitlement, and it would reshape the navbar on an enforcement flip and orphan an existing pin. All of this is **dormant** today (enforcement OFF), so nothing changes pre-flip.

### 7. The number leaves by clipboard, never by coupling

Long-pressing the pill (and a copy icon in the expanded header) copies the current value with a brief "Copied" confirmation; the user pastes into any amount field. ~5 lines, no coupling.

Alvin did **not** ask for insertion into the transaction editor, and the booking flagged that it must be scoped in or ruled out rather than built silently. A real Insert button is **ruled out**: it needs a cross-feature channel from the overlay into the editor's state plus focus tracking, and helps exactly one screen. Clipboard works in Add/Edit, Budgets, Goals and Debts alike, and removes the transcription error that pure-scratchpad would leave in place.

### 8. Only the pill drags, and the bar says nothing

The **collapsed pill** drags freely on the vertical axis and snaps horizontally to the nearest edge on release, clamped so it can never sit under the status bar or the 74dp bottom bar. The **expanded panel does not drag** — it anchors to the pill's current side. One position model instead of two, no question of whether the two positions stay linked on collapse.

The bottom bar shows **no indicator** while the bubble is open. `PlayfulBottomBar` renders selection as a single pink active pill meaning "the module you are navigated to"; highlighting Calculator while the user browses Records would put two active pills in the bar and assert a navigation state that isn't true. The bubble is a visible, clamped-on-screen object — it is its own indicator, and a better one.

## Consequences

1. **A one-release upgrade trap must be closed in the same slice.** A device backgrounded on Calculator before this ships has `"calculator"` persisted in the nav-restore store. `NavRegistry.byId::containsKey` still returns true for it, so `moduleToRestore` would return it and the `NavHost` would start on a deleted graph route. The guard must become *navigable*, not *known* — a one-line change at [NavbarViewModel.kt:86](../../app/src/main/java/com/iponlove/app/navigation/NavbarViewModel.kt#L86) plus a case in the existing `NavRestorePolicyTest`. Going forward the id can never be persisted again, since `currentModuleId` is derived from graph membership.
2. **The registry's every-module-has-a-graph invariant is broken deliberately.** `isInGraph(CALCULATOR)` is permanently false. This is benign for the two consumers (`isSelected`, and `moreSelected`'s `inSomeModuleGraph && visiblePins.none { isInGraph }`), but any future code that assumes a registry entry implies a route must consult `navigable`.
3. **A tier-1 test surface appears where the booking expected none.** The clamp-and-snap decision is a pure function (`snapPosition(dragEnd, bounds)`), unit-testable without an emulator — better than the booking's "no new tier-1 test surface anticipated" for a UI-heavy slice. `CalculatorEngine` and its existing `CalculatorEngineTest` are untouched.
4. **Losing the full screen means losing the big keypad.** A user who liked a full-screen calculator gets a compact one. Accepted: the value is continuity, not key size, and the keypad stays a 4×5 grid.
5. **No schema, Room, Supabase, or sync impact.** The calculator has no data layer, and the bubble persists nothing.

## Rejected alternatives (summary)

| Rejected | Why |
|---|---|
| Keep the route + a "pop out" button | Doesn't remove the round trip; two code paths for one feature |
| Bubble-only with the route kept as a shell | Ships unreachable dead code; registry still lies about navigability |
| Dismiss the bubble on every background | Destroys work the user didn't ask to lose; fights ADR-0023's posture |
| Persist the bubble across process death | Scratch value outliving a force-stop; restore-ordering complexity for no gain |
| Icon-only collapsed bubble | Hides the number — trades a screen swap for a tap instead of removing the trip |
| Always-expanded panel, no collapsed state | Permanent ~40% screen blocker |
| Back closes the bubble | Makes the most-pressed button in the app a value-destroyer |
| Hide the bubble on Add/Edit transaction | Hides it exactly where the computed total is about to be typed |
| Bubble above the coach-mark overlay | Can cover a first-run tutorial tooltip |
| Compact locked upsell card inside the bubble | Needs a new `FeatureLockedPanel` variant; worse pitch than the real paywall |
| Hide Calculator from the registry when locked | No precedent; reshapes the navbar on enforcement flip; orphans pins |
| Insert result into the amount field | Cross-feature coupling + focus tracking; helps one screen; beyond the stated ask |
| Draggable expanded panel | Two position models and a link-on-collapse question, for a scratchpad |
| Active pill on the Calculator tab while open | Two active pills; asserts a navigation state that is false |
