# Receipt scan: bundled on-device OCR into a prefilled draft, ungated, inferring from the user's own history

## Context

Booked as [v1.7.3 Item 2](../build/v1.7.3.md#item-2--receipt-scan-photograph-a-receipt-into-a-prefilled-transaction) off a 2026-07-29 competitive gap analysis against the Tarsi budget tracker, and grilled the same day. The ask: photograph a receipt, get a transaction draft, instead of typing it.

Five codebase findings shaped the design before any decision was made:

1. **The app has no camera capture at all.** Every image path is a gallery picker — receipts ([AddTransactionScreen.kt:245](../../app/src/main/java/com/iponlove/app/feature/transactions/presentation/AddTransactionScreen.kt#L245)), notes ([NoteEditorScreen.kt:93](../../app/src/main/java/com/iponlove/app/feature/notes/presentation/NoteEditorScreen.kt#L93)), the couple banner ([CoupleOverviewBody.kt:189](../../app/src/main/java/com/iponlove/app/feature/couple/presentation/CoupleOverviewBody.kt#L189)). No `CAMERA` permission is declared, no `TakePicture`, no CameraX. "Scan a receipt" today means leaving the app, shooting in the Camera app, and coming back to pick from the gallery.
2. **`ReceiptStrip` is dead last on the editor form** ([AddTransactionScreen.kt:391](../../app/src/main/java/com/iponlove/app/feature/transactions/presentation/AddTransactionScreen.kt#L391)) — below Type, Amount, Account, Category, Note, Date and both toggles. Any scan affordance placed there is found *after* the user has already typed everything it would have filled in, which is why converging with the existing attach flow was rejected as the primary entry (decision 3).
3. **The free receipt-photo cap is zero** — `maxReceiptPhotos = 0` free / `3` premium ([PlanLimits.kt:42](../../app/src/main/java/com/iponlove/app/core/entitlement/PlanLimits.kt#L42)), set deliberately by v1.6.7 Item 9. A premium differential for this feature therefore already exists without inventing one.
4. **The prod release APK is 5.1 MB** — unusually lean, which makes the bundled-vs-downloaded OCR model a real trade rather than a rounding error (decision 1).
5. **Receipts are stored downscaled** to 1080 px / JPEG 85 ([CompressReceiptUseCase.kt:40](../../app/src/main/java/com/iponlove/app/feature/transactions/domain/usecase/CompressReceiptUseCase.kt#L40)) — below what reliably reads small thermal print, so OCR must run upstream of that pipeline, not on its output (decision 4).

The booking's own research finding also stands: **OCR proper is classical on-device ML, not an LLM**, so this item genuinely leaves [Post-V1 Horizon #3](../build/project-build-progress.md) (AI companion) rather than depending on its unresolved funding model — unlike [v1.7.3 Item 3](../build/v1.7.3.md#item-3--voice-command-natural-language-voice-input-to-log-a-transaction) (voice), which came straight back and was deferred the same day.

## Decision

### 1. Extraction is bundled, on-device, classical ML — never a cloud call

**ML Kit Text Recognition v2, Latin script, the bundled artifact** (`com.google.mlkit:text-recognition`, model shipped inside the APK) — not the Play-Services-delivered variant.

The trade is ~4 MB of download size (5.1 MB → ~9 MB, **to be measured at build, not assumed**) against a guarantee. Bundled means scanning works on first launch, offline, on any device, with no dependency on Google Play Services being present and healthy, and **no "getting ready, connect once" state to design, build, test or explain**. The downloaded variant keeps the app at ~5.4 MB and would work in almost every real case — the model arrives at install time and is shared across apps — but it purchases 4 MB by introducing a failure mode into a feature the app otherwise promises works offline.

The relative framing ("doubles the app") was rejected as misleading during the grill: 9 MB is not a size any user declines, and the offline-first stance the app commits to everywhere else is worth more than the 4 MB.

Cloud OCR was rejected outright — per-call cost, a network requirement, and receipt images leaving the device, all for a feature that must work in a grocery queue on bad signal.

### 2. Camera capture arrives with **no new camera permission** — and that must survive future edits

Capture uses `ActivityResultContracts.TakePicture` with a `FileProvider` output URI. **`android.permission.CAMERA` is deliberately never declared.** `ACTION_IMAGE_CAPTURE` requires no runtime permission *unless the app declares CAMERA* — declaring it is what activates the requirement. Adding that line "for correctness" would introduce a permission prompt this design specifically avoids, and would do so silently, since the flow still works after the user grants it.

The app's permission list stays: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `USE_BIOMETRIC` — **plus** the one addition decision 7 forces, and nothing else.

### 3. Two doors, one capture path — and the Records door is a scrollable action wheel

Both entry points ship:

- **A "Scan a receipt" CTA at the very top of the New transaction form**, above the Type row. It cannot be missed, requires no nav-graph change, and works no matter how the editor was reached.
- **A scrollable FAB wheel on Records.** One FAB position; the armed action sits large in the standard spot with the others peeking smaller above it. Swiping vertically moves a different action into the armed slot, growing it. Ships with two — `＋` (new transaction) and `📷` (scan).

Three rules govern the wheel, all settled at the grill:

- **It always resets to `＋`.** The armed slot never persists across visits. Predictability beats the saved swipe: a big button that changes meaning between visits makes a half-glance tap fire the wrong thing, and typing remains the common case. This also means **no stored preference** — nothing in DataStore, no per-device/per-account question.
- **The small buttons are directly tappable.** Tapping the small `📷` opens the camera without swiping first. This is what rescues the wheel's discoverability problem: a user who never realises it scrolls can still reach the camera in one tap, so the swipe is polish rather than a toll gate.
- **It is built generic over a list of actions**, because a third slot (🎤 voice) is expected when [Item 3](../build/v1.7.3.md#item-3--voice-command-natural-language-voice-input-to-log-a-transaction) un-defers. Adding it must be an entry in a list, not a rewrite — per the standing "build the general facility, not the use-case feature" rule.

A one-time coach mark anchors on the wheel. This is precisely [ADR-0059](0059-coach-mark-coverage-is-discoverability-triage.md)'s bar — a step exists only where the interaction **isn't inferable from looking at the screen**, and a FAB that scrolls is not inferable.

The scan action offers **camera or gallery**, not camera alone. The gallery leg reuses the picker already in the code and covers **GCash/Maya/GrabPay confirmation screenshots** — high-contrast, perfectly legible, and plausibly a larger share of this audience's spending than paper receipts.

### 4. What is *read* and what is *inferred* are different things, and only the inferred is marked

**Read off the paper** — `Amount`, `Date`, and the merchant name into `Note`. `Type` is forced to `EXPENSE` (a receipt is never income).

**Inferred from history** — `Category` and `Account` (decision 5).

Only the two inferred fields carry a caption ("from your last SM Supermarket visit"). The three read fields carry none, because **the receipt photo is displayed at the top of the form during review** — they are verifiable by looking, and six captions on one form is noise. The inferred pair appear nowhere on the receipt, so without a caption a user comparing form to photo has no way to account for them.

OCR runs on the **full-resolution captured image**, before `CompressReceiptUseCase` downscales to 1080 px for storage (finding 5). The order is: capture → recognise → parse → *then* compress for persistence.

Parsing rules pinned at the grill, all pure and unit-testable given fixed recognised text:

- **Amount** — prefer the line matching `total`/`amount due`, explicitly excluding `subtotal`, `vatable`, `vat`, `cash`, `tendered`, `change`. Falling back to "largest number on the page" is wrong on a PH receipt, where `CASH` tendered routinely exceeds the total.
- **Date** — `07/08/2026` is genuinely ambiguous. Resolve month-first (PH convention), then sanity-bound the result: never in the future, never more than ~18 months old. A result failing the bound falls back to today. **An ambiguous date is marked like an inferred field**, since it was guessed, not read.
- **Merchant** — the largest text near the top, using ML Kit's bounding boxes. Written to `Note` **cleaned** (title-cased, trailing branch/store codes stripped), which is what makes it self-reinforcing input for decision 5.

**No cropping and no edge detection.** Retake (decision 8) is the fix for a bad frame.

### 5. Category and account are inferred from the user's **own transaction history** — not a merchant table, not an LLM

On scan, the cleaned merchant string is matched against the user's own past transactions' `Note` values (normalised: case-folded, punctuation and branch numbers like `#0142` dropped, token-subset match either direction). The category and account most frequently paired with that merchant win, tie-broken by recency.

Three properties make this the right mechanism:

- **It needs no new schema.** It queries `transactions`, which already exist — per the standing "re-trace the existing mechanism before proposing a new field" rule. **Room stays v31, and there is no Supabase migration.**
- **It fits the market.** A hardcoded PH chain table (Jollibee → Dining Out, Shell → Transport) knows only what is hardcoded, misses every sari-sari store and regional name, guesses wrong for anyone whose categories are named differently, and needs maintaining forever. History knows whatever the user actually shops at.
- **It bootstraps itself.** The first scan at a new merchant infers nothing — but it writes the cleaned merchant into `Note`, so the second scan there has something to match. This is worth stating in copy expectations: the feature gets better with use, and is at its weakest on a fresh account.

An LLM parser was rejected: it inherits Item 3's whole cost-and-device problem (cloud per-call cost, or Gemini Nano's ~12 GB RAM / flagship-chipset floor that excludes this app's market *and* the maintainer's own test device) to solve a problem regex and a history lookup already solve.

**Own rows only.** Partner transactions are never consulted — partner data arrives through redacting views ([ADR-0005](0005-redacting-partner-views-for-convergence.md)) and their category/account ids are not usable on the user's own row anyway.

### 6. Scanning is **not gated**. The existing receipt-photo cap is the premium differential

**No new `Feature` enum entry.** Scanning a receipt is recording your own money — just faster — and [D5](../build/subscription-paywall-design.md) is explicit that *"recording your own money is never gated … the wall is around power and polish, never around the core ledger."*

The premium differential arrives for free from finding 3: on the same scan, a premium user's photo is **kept as an attachment**, a free user's is **read and dropped**. This is structurally the [Export decision](../build/v1.7.0.md#item-6--general-export-facility-csv--pdf--zip-receipt-photos-included) repeated — CSV stayed free because trapping a user's data reads badly against D5, and the gate landed on the photo-bundling formats instead, "non-cannibalising by construction" because free has no photos to bundle.

The honest case against was heard and rejected: this is plausibly the strongest single reason anyone would pay ₱249, and `CALCULATOR` — a much smaller feature — is gated. It loses because Calculator is a side tool and the recurring calendar is a *view*, whereas scanning is a **method of entering transactions**, which is the one thing D5 names as never wallable. Manual entry remains untouched and fully capable, so nothing is blocked; only the kept photo differs.

A monthly free-scan allowance was also rejected — the paywall design states *"No free trial — the generous free tier **is** the trial"*, and a counter would need storing, resetting and syncing for a soft limit.

### 7. The photo has two destinations with different rules

- **In-app attachment** — subject to `maxReceiptPhotos` via the existing `CheckReceiptPhotoCapUseCase` path. This is the premium-differentiated copy: it syncs, renders on the transaction, appears in the partner combined view, and bundles into a PDF/ZIP export.
- **Gallery copy** — written to a dedicated **`Pictures/Love, Ipon`** album, never dumped into the camera roll root. **Free for everyone and never gated**: it costs the app nothing (no Storage egress, no sync, no cap), and it is the user's own photo on their own phone.

The gallery copy is the **one thing that forces a manifest change**. `MediaStore` writes need no permission on API 29+, but `minSdk = 26` still supports Android 8.0/9.0, where they need `WRITE_EXTERNAL_STORAGE`. Alvin's call: **declare it with `android:maxSdkVersion="28"` and request it at runtime only on those versions**, so every user gets the gallery copy, rather than silently degrading on old phones. This is the sole permission added by the whole feature; decision 2's no-CAMERA property is unaffected.

Gallery saving is **a Settings → Finance toggle, default ON**. The switch exists because gallery photos sync to Google Photos, so a user's grocery and pharmacy receipts land in a backed-up, possibly shared camera roll — that should be a choice, not a surprise.

### 8. A failed read stays in the camera

Nothing readable → **"Couldn't read that one"** with **Retake** and **Enter manually**. A failed scan is nearly always a bad frame (glare, angle, motion), and retaking is both the actual fix and one tap away without navigating anywhere. Falling through to a blank form was rejected: it makes typing the path of least resistance precisely when a second photo would have worked.

A **partial** read is not a failure — whatever was found fills in, the rest stays blank, and the form opens. Only a completely empty result triggers the retake prompt.

## Consequences

- **No schema change, no Room change (stays v31), no Supabase migration**, and **no impact on [Item 1](../build/v1.7.4.md#item-1--freeze-the-cross-platform-contract-from-web-phase-0-w2)'s cross-platform contract freeze** — the parser is client-local, mints no deterministic ids, and writes only through existing synced columns.
- **Web parity is upload-only — and the engine, not the camera, is the real gap** (Alvin, 2026-07-29). The web client takes a *picked* image only; no camera capture. That leg costs little: browsers can in fact open a camera (`<input type="file" accept="image/*" capture>`, or `getUserMedia`), so upload-only is a **scope decision, not a platform limit** — and decision 3's gallery leg, the GCash/Maya screenshot case, is the one that ports cleanly and is plausibly the larger share of use anyway. The load-bearing gap is **decision 1**: there is no ML Kit on web, so a web scan needs either a wasm engine (Tesseract.js — materially worse on thermal print) or a cloud OCR call. A per-call cost would reopen **decision 6's ungated ruling on web only**, since "costs nothing per use" is exactly what made not gating it free. Decision 7's `Pictures/Love, Ipon` copy has no web analogue at all. None of this changes the Android design — it means the web build must pick its own engine and **cannot inherit this ADR wholesale**.
- **APK grows ~4 MB.** Measure at build; if it lands materially above ~9 MB, decision 1 is the thing to revisit, not the feature.
- **A future edit that adds `<uses-permission android:name="android.permission.CAMERA" />` silently regresses decision 2** by introducing a runtime prompt where there was none. The manifest carries a comment saying so, in the style of the existing `allowBackup` note.
- **`WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) appears in the Play listing.** Accepted deliberately over silently dropping the gallery copy on Android 8/9.
- **The feature is weakest on a fresh account** — no history means no category or account inference until the user has scanned or logged a given merchant once. This is inherent to decision 5 and should shape the copy, not be papered over.
- **A wrong inferred `Account` corrupts a balance**, where a wrong `Category` only misfiles a chart. This asymmetry is why decision 4 marks both inferred fields rather than letting them arrive silently.
- **Three slices, verified on-device between each** (per the house rule): (1) scan → filled form, including camera/gallery, reading, parsing, review, retake, attach, gallery save and its setting; (2) history inference for category + account with their captions; (3) the FAB wheel and its coach mark. Slice 1 alone is the feature end-to-end.

## Rejected alternatives (summary)

| Alternative | Why rejected |
|---|---|
| Converge with the existing `ReceiptStrip` attach flow | It sits last on the form — found only after the user has typed everything it would have filled |
| Play-Services-delivered OCR model | Saves ~4 MB, buys a "getting ready" failure state and a Play Services dependency into an offline-first feature |
| Cloud OCR API | Per-call cost, network requirement, receipt images leaving the device |
| LLM parsing of the recognised text | Inherits Item 3's cost/device problem to solve what regex plus a history lookup already solve |
| Hardcoded PH merchant → category table | Knows only what's hardcoded, misses regional and sari-sari stores, needs maintaining forever |
| Auto-arm the last-used FAB action | The big button would change meaning between visits, so a half-glance tap fires the wrong thing |
| A `RECEIPT_SCAN` premium gate | Walls off a *method of recording own money*, which D5 names as never wallable; the photo cap already differentiates |
| Free monthly scan allowance | Contradicts "the generous free tier **is** the trial"; a synced counter for a soft limit |
| Cropping / edge detection | Retake is the fix for a bad frame, at a fraction of the complexity |
| Blank form on a failed read | Makes typing the path of least resistance exactly when a second photo would have worked |
