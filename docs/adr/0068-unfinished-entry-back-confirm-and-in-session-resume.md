# New transaction: Back prompts Discard/Save as draft; unsaved fields survive tab-switches as an in-memory "Unfinished entry"

**Status:** accepted (2026-08-07, grilled) — [v1.7.3 Item 16](../build/v1.7.3.md#item-16--new-transaction-back-confirm-on-unsaved-changes--in-session-survival-across-navigation). Interacts with [ADR-0039](0039-addtxn-route-not-restored-across-tabs.md) (kept unchanged) and reuses [ADR-0066](0066-transaction-drafts-parking-area.md)'s `SaveDraftUseCase`.

## Context

Alvin, 2026-08-07: two related asks on New transaction: (1) pressing Back with partially filled fields should prompt Save as draft / Discard, not silently lose input; (2) unsaved, un-drafted input should survive navigating elsewhere in the app, resetting only on an explicit discard or an app/process restart.

This runs straight into [ADR-0039](0039-addtxn-route-not-restored-across-tabs.md), which made `switchTab` deliberately pop Add/Edit off the back stack **unsaved** the moment you leave for another tab — built specifically to kill a bug where Add would resurrect on top of an unrelated tab. Ask (2) wants the input to survive exactly the navigation ADR-0039 was written to make destructive.

Two other facts from the code shaped the design:

- The form already mirrors itself into `SavedStateHandle`, which survives rotation/process death **while the screen stays on the back stack**, but not leaving the screen (`AddTransactionViewModel`'s own doc comment: "the route alone does not [survive]").
- `AddTransactionViewModel.onCleared()` unconditionally deletes any unsaved scanned receipt file the instant the ViewModel is destroyed — which happens on every tab-switch pop today.

## Decision

### 1. Scope: new transactions only, not Edit

Edit already has a safe story — the original row is untouched in the database until Save, so leaving mid-edit and reopening just shows the unchanged saved row. The pain described is specifically "I typed something into a **new**, nowhere-yet-saved transaction and lost it." Extending to Edit is a candidate follow-up item, not part of this one.

### 2. The survived state is in-memory only — never a silently-written real draft

Rejected: auto-creating/updating a real `transaction_drafts` row on every navigation away. That would mint (or mutate) an entry in the user's Drafts list every time they merely tabbed away by accident — Drafts is supposed to mean "things I deliberately parked" (see `Save as draft`, which already exists and is untouched by this item). Chosen: hold it in a small in-memory holder that isn't tied to the screen's own lifecycle, so it dies with the app process — the same as unsaved input dies today if you force-quit. This is a *weaker* durability promise than a real Draft, on purpose: Draft is for "I'll get back to this in a few days," this is for "don't lose my typing if I glance at another tab."

### 3. Named "Unfinished entry" — a new, distinct glossary term

Not "draft," not "pending transaction," not "in-progress editor state" (all already claimed or ambiguous — see `CONTEXT.md`'s `Draft` entry, whose own text distinguishes "in-progress editor state, gone if the screen closes" from a real Draft; that sentence is now inaccurate and is amended by this ADR). **Unfinished entry**: the in-memory-only, single-outstanding, un-drafted state of a new transaction form, remembered only for the current app session.

### 4. What survives: the same field list `SavedStateHandle` already mirrors, minus the three receipt-image fields

Type, amount, account, to-account, category, note, private toggle, date, "paid for partner" + owed amount, transfer fee. The receipt photo (and its temp/compressed files) does **not** survive a tab-switch — `onCleared()`'s existing unconditional delete-on-destroy behavior needs **no changes**, since a tab-switch destroying the ViewModel destroying the photo is now the *intended* outcome, not a bug to route around. Re-scanning after a tab-switch is one tap on the same button.

Rejected: also making the photo survive. Would require distinguishing "destroyed because the user explicitly gave up" from "destroyed because they tabbed away" inside `onCleared()`, multiplying the risk of getting file-lifecycle cleanup wrong for a comparatively rare case (mid-scan-review tab-switch) — see [v1.7.0 Item 14](../build/v1.7.0.md#item-14--orphaned-receipt-files-are-never-cleaned-up-device-storage-leak) and ADR-0062 decisions 9-10 for how much care that leak class already needed.

### 5. Mechanism: an app-scoped singleton holder, not hoisted composable state or a shared ViewModel

Three options were weighed:

- **(chosen) A small `@Singleton` object**, written to on every field change (alongside the existing `SavedStateHandle` write — no new plumbing there), read once when a fresh New Transaction screen opens with no draft id and no edit id.
- Hoisting the state into `IponAppContent`, the way the Calculator overlay's state lives above the nav layer (ADR-0058). Rejected: a bigger structural change, threading a new piece of state through the app's root composable for something that only needs to be read at one moment (screen open), not rendered continuously like the Calculator bubble.
- A ViewModel scoped above the individual route (Activity- or nav-graph-scoped), shared between the ⊕ and 📷 entry points. Rejected: no material difference from the singleton for this need (survive individual screens, still die with the process) — just more Android-lifecycle wiring for the same result.

### 6. Resume applies at both the ⊕ button and the Records 📷 scan button — no special case for scan

Both open a "plain" new transaction and both silently resume the Unfinished entry if one exists. Explicitly accepted trade-off: scanning a **second, different** receipt while an old Unfinished entry is pending will land the new photo's read fields on top of the old text (e.g. an old note next to a new amount). Not special-cased to force a blank form on scan — kept consistent ("any way of opening New transaction resumes what's pending") over protecting this one, comparatively rare sequence.

Settling a draft or opening Edit are unaffected either way — they load their own explicit content, and any unrelated Unfinished entry stays parked in the background untouched, waiting for the next plain-new-transaction open.

### 7. Back-confirm prompt: two buttons, Discard and Save as draft — no separate "Keep editing"

Dismissing the dialog (tap outside, or Back again while it's open) closes it and returns to the form with input untouched (`onDismissRequest`) — functionally "keep editing," just with no dedicated button for it, since Android dialogs already support this for free. `Save as draft` in the dialog calls the same `SaveDraftUseCase` the existing top-bar button already does.

### 8. Prompt trigger: reuse `canSaveAsDraft` (`!isEditing && hasDraftContent`), but scoped to a genuinely new, never-parked transaction

`canSaveAsDraft` is also `true` the instant you open an *existing* draft to settle it, since a draft by definition already has content. Left as-is, the prompt would fire even when backing out of a draft you opened and touched nothing — noisy, since that data is already safely parked. **The prompt itself is scoped to the plain ⊕/📷 entry points** (no draft id, not editing), the same scoping as the resume behavior in decision 6. Settling a draft that you *do* edit and then back out of still calls `SaveDraftUseCase`/discards normally today — this ADR doesn't change that path, it just doesn't add a new prompt on top of it.

### 9. No visible indicator elsewhere in the app that an Unfinished entry exists

Unlike Drafts' pinned "Drafts (N)" card (Item 8) — there's at most one Unfinished entry at a time, it can't accumulate, and it resolves itself the instant you reopen New transaction, explicitly discard it, or the app restarts. A second "you have something pending" affordance next to Drafts' card would compete with a mechanism that's supposed to read as invisible continuity, not a queue.

## Consequences

- ADR-0039's `switchTab` pop is **unchanged** — that fix stays in force; this ADR builds a separate, additive mechanism on top of it rather than reopening it.
- `AddTransactionViewModel.onCleared()` needs **no changes** — its existing unconditional receipt-file cleanup on destroy is now correct for every destroy path, tab-switch included (decision 4).
- New: an app-scoped singleton (name TBD at build, e.g. `UnfinishedEntryHolder`) — small, no Android lifecycle dependencies, straightforward to unit-test standalone.
- No schema/Room/Supabase change — this is purely in-memory, local, ephemeral state.
- `CONTEXT.md`'s `Draft` entry needs a one-line amendment (its aside about "in-progress editor state, gone if the screen closes" is no longer universally true) plus a new `Unfinished entry` glossary entry.
- Edit is explicitly out of scope; extending Back-confirm/resume to Edit is a candidate follow-up item.

## Rejected alternatives (summary)

| Alternative | Why rejected |
|---|---|
| Auto-write a real `transaction_drafts` row on every navigation away | Blurs Drafts into "everything I ever tabbed away from," not "what I deliberately parked." |
| Also survive the unsaved receipt photo across a tab-switch | Requires distinguishing explicit-discard from tab-switch inside `onCleared()`, for a comparatively rare case; re-scanning is one tap. |
| Hoist state into `IponAppContent` (Calculator-style) | Bigger structural change to the app root for something read once per screen-open, not rendered continuously. |
| A shared Activity/nav-graph-scoped ViewModel | No material difference from a plain singleton for this need; more Android-lifecycle wiring for the same result. |
| Force a blank form when scanning while an Unfinished entry is pending | Special-cases one entry point; kept both entry points consistent instead. |
| Fire the Back-confirm prompt while settling an untouched draft | Noisy — that data is already safely parked, nothing new is at risk. |
| A visible "Unfinished entry" indicator (badge/card) | Only one can exist at a time and it resolves itself quickly; would compete with Drafts' own card for the same attention. |
| A third "Keep editing" button on the Back-confirm dialog | Dismissing the dialog already does this for free via `onDismissRequest`; a labeled button adds nothing. |

## Suggested build

Opus, high effort — collides directly with ADR-0039's hard-won fix and introduces a new piece of app-scoped state; not a pattern-follow.
