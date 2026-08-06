# Quick Add gains scanning and drafts by reusing the full form's use cases, not its state machine

**Status:** accepted (2026-08-07, grilled) — [v1.7.3 Item 14](../build/v1.7.3.md#item-14--quick-add-widget-sheet-notes-field-receipt-attachment-scangallery-choice). Depends on [ADR-0062](0062-receipt-scan-ocr-prefilled-draft.md) (receipt scan) and [ADR-0066](0066-transaction-drafts-parking-area.md) (transaction drafts) — build-ordered after Item 8's data/domain slice lands.

## Context

Alvin asked for the home-screen widget's Quick Add sheet to gain the same `📷 Scan receipt` / `🖼️ From gallery` choice as the full New Transaction form, plus a Notes field and a receipts field — none of which it has today. `QuickAddActivity`/`QuickAddSheet` is a deliberately stripped-down `ModalBottomSheet` (Type, Amount, Category, Account only); `QuickAddViewModel.save()` hardcodes `note = null` and mints the transaction's `id` only at save time, with no receipt-image path at all.

Two findings made this more than a pattern-follow:

1. **Quick Add has no connection to the app's main navigation.** `QuickAddActivity` is its own `ComponentActivity`, launched straight from the widget — it has no `NavController`, so it can't reach `SubscriptionScreen` the way every other locked-tap site does (`navController.navigate(subscriptionRoute(source))` inside `IponApp`'s graph).
2. **The full editor pre-generates its transaction id** (`TransactionEditorState.id`) specifically so a scanned receipt image has something to key against before Save. Quick Add's lazy, save-time `UUID.randomUUID()` doesn't support that.

## Decision

### 1. Full OCR pipeline, reusing `ScanReceiptUseCase` as-is

Quick Add's scan buttons run the same read (Amount/Date/merchant) + infer (Category/Account via [[Merchant memory]]) pipeline as the full form, not a stripped "just attach a photo" mode. A capture-only mode would be a second, divergent receipt-handling code path (EXIF, temp-file lifecycle, compression threading) for no product benefit — the whole point of the request is parity with the full form's two-button pattern, and "read it for you" is the actual speed win, not merely offering a picker.

### 2. Quick Add gets a third exit, `Save as draft`, always visible

Reuses Item 8's `SaveDraftUseCase`/`PromoteDraftUseCase` unchanged. Shown unconditionally (not only after a scan) — the parking-area idea applies just as much to an unfinished hand-typed quick-add as a scanned one, and gating it on scan-having-happened would be a restriction Quick Add invents that the full form doesn't have.

**This makes Item 14 depend on Item 8's data/domain slice existing** — Quick Add cannot ship its draft exit before `SaveDraftUseCase` does.

### 3. `QuickAddViewModel` stays its own lighter state machine — composition, not inheritance of `TransactionEditorState`

Considered folding Quick Add into the same `TransactionEditorViewModel`/`TransactionEditorState` the full form uses. Rejected: that state carries date picking, transfer destination account, private/shared toggling, `isAdjustment`, and manual (non-scan) receipt attach — none of which Quick Add has today, and pulling all of it in would let the "quick" sheet slowly regrow into a second copy of the full form, defeating the reason it exists as a separate lightweight entry point.

Instead, `QuickAddViewModel` keeps its own `QuickAddForm` (now gaining `note`, a pending receipt image, and draft intent) and calls the same `ScanReceiptUseCase` / `SaveDraftUseCase` / `PromoteDraftUseCase` the full form calls. It now **pre-generates its id eagerly** (at sheet creation, mirroring `TransactionEditorState.id`) instead of lazily at `save()`, so a scanned image has an id to key against from the moment capture succeeds.

### 4. A locked scan tap deep-links to `MainActivity`'s `SubscriptionScreen`, then finishes `QuickAddActivity`

`QuickAddActivity` has no `NavController` to route through. Rather than build a second, sheet-hosted upsell UI (which still can't complete a purchase without leaving to `MainActivity` for the Play Billing flow), a locked tap launches `MainActivity` with a deep-link intent that lands directly on `subscriptionRoute(source)` — the same route every other locked-tap site already navigates to — then finishes the widget activity. Reuses the existing `"receipt_scanning"` analytics upsell source string unchanged; no other feature in the codebase splits its upsell source by originating screen, so a widget-specific split would be new granularity nobody asked for.

### 5. Notes is an always-available manual field, not scan-only

`note` stops being hardcoded to `null`. It's a free-text field always present in the sheet, prefilled by a scan's cleaned merchant name (same as the full form) but editable with or without a scan — this is really the actual gap in the request, since Quick Add currently has no way to jot anything down at all.

### 6. The sheet stays a scrollable `ModalBottomSheet`, not a full-screen activity

Scan buttons, a photo preview (for verifying the auto-filled Amount/Date/merchant against the receipt, matching ADR-0062's read-fields-overwrite rationale), Notes, and the draft action make the sheet taller than today. Wrapping the content `Column` in `verticalScroll` (same fix already applied to `NotificationsScreen`) keeps it recognizably "quick" — no activity transition, no second theme/nav stack — while still letting it grow to fill most of a shorter screen.

### 7. Cancel, swipe-dismiss, and Back all route through one `viewModel.onAbandon()`

Today `onDismiss = ::finish` is wired directly to the bottom sheet's dismiss request (which M3 fires on both swipe and system Back) and to the `Cancel` button, with no ViewModel call — there's nothing to clean up yet. Once scan is added, an abandoned sheet can leave behind a captured/compressed receipt file. All three dismiss paths now call `viewModel.onAbandon()` first, which deletes any unsaved scanned receipt file — mirroring the full editor's abandon path — before `finish()`. Leaving this to the periodic age-based sweep alone would reopen the leak class [v1.7.0 Item 14](../build/v1.7.0.md#item-14--orphaned-receipt-files-are-never-cleaned-up-device-storage-leak) was built to close, just with a longer window.

### 8. `Save as draft` shows a toast before dismissing

Regular `Save` gets implicit feedback — the balance widget updates on the home screen once the sheet closes. Draft-save writes nothing visible from the home screen (no widget change, and the pinned "Drafts (N)" card lives inside the app on Records, not on the widget), so a user tapping it from the widget gets zero signal that anything happened. A one-line `Toast.makeText(...)` ("Saved to drafts") before `finish()` closes that gap without needing a full snackbar host for one activity.

## Consequences

- No schema/Room change of its own — Item 14 sits entirely on top of the columns/tables Items 2 and 8 already add. No Room version bump.
- **Build-ordered after Item 8's Slice 1** (data + domain), since `SaveDraftUseCase`/`PromoteDraftUseCase` must exist before Quick Add's third exit can call them.
- `QuickAddForm` and `QuickAddViewModel` grow (note, pending image, draft intent, eager id generation) but stay their own type — no shared inheritance with `TransactionEditorState`.
- A new cross-activity paywall hand-off pattern (deep link from a non-nav-graph `ComponentActivity` into `MainActivity`'s `subscriptionRoute`) is established here; any future widget-originated premium touchpoint should reuse it rather than inventing another.

## Rejected alternatives (summary)

| Alternative | Why rejected |
|---|---|
| Capture-only "attach a photo, no OCR" scan mode | A second, divergent receipt code path (EXIF, temp-file lifecycle, compression) for no product benefit; the request explicitly wants parity with the full form's two-button pattern. |
| Unify Quick Add on `TransactionEditorViewModel`/`TransactionEditorState` | Pulls in date picking, transfers, private/shared toggling, `isAdjustment`, manual attach — none of which Quick Add has; risks regrowing into a second full form. |
| In-sheet upsell card instead of a `MainActivity` deep link | Still can't complete a purchase without leaving to `MainActivity` for Play Billing; only delays the same jump while adding a new upsell UI to design and maintain. |
| Widget-specific analytics upsell source string | No other feature splits its upsell source by originating screen; new granularity nobody asked for, fragments an otherwise-aggregate metric. |
| `Save as draft` visible only after a scan | An arbitrary restriction Quick Add would invent that the full form doesn't have; the parking-area idea applies to hand-typed entries too. |
| Full-screen activity instead of a scrollable bottom sheet | Structurally closer to the full form; undercuts the reason Quick Add exists as a separate lightweight entry point. |
