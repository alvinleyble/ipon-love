# Receipt scan: bundled on-device OCR into a prefilled draft, ungated, inferring from the user's own history

> **⚠️ Amended 2026-08-03/2026-08-04 — second, narrow review + captain rulings.** A follow-up design review (seeded by [v1.7.3.md](../build/v1.7.3.md) Item 5's nine gaps; full findings and `file:line` evidence in `data/ipon-love-receipt-gaps/report.md`) and two captain decisions changed this ADR after it locked, without reopening the engine/gating/permission/entry-point/FAB questions wholesale: **decision 6 is reversed** — receipt scanning is now fully paywalled (`decision-free-tier-photo-drop-copy.md`, 2026-08-03); **decision 3**'s New-transaction-form entry changes from a single CTA + chooser sheet to two direct buttons (`decision-flow-confirmations.md`, 2026-08-04); **decision 1** stands, with the ~4 MB estimate corrected to measured per-ABI figures and its own ~9 MB revisit trigger fired and resolved (`decision-apk-size-trigger-fired.md`, 2026-08-04); **decision 7**'s gallery-copy timing, previously unanswered, is now pinned. Two new decisions (9, 10) close the temp-file-ownership and EXIF gaps the first grill structurally could not reach, having had no receipt corpus and no running code. The title above still reads "ungated" — kept as written for history; decision 6 below is where that changed. Superseded reasoning is kept struck-through in place rather than deleted, per house style.

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

~~The trade is ~4 MB of download size (5.1 MB → ~9 MB, **to be measured at build, not assumed**) against a guarantee.~~ **Corrected 2026-08-04 — measured on the release build (R8 + resource shrinking), per delivered ABI** (`data/ipon-love-receipt-gaps/report.md` §3, reproducible in ~4 minutes): **arm64-v8a 5.24 → 17.14 MiB (+11.90 MiB)**, **armeabi-v7a 5.24 → 13.06 MiB (+7.82 MiB)**. The weight is an uncompressed native OCR pipeline `.so` (11.1 MB arm64 / 6.8 MB armeabi-v7a — page-aligned native libraries ship stored, not deflated) plus ~1.3 MB of `.tflite`/`.fb` model assets, so packaging cannot reduce it. **The universal APK (~45.8 MiB, all four ABIs) is a build artefact, not a delivered size** — Play's AAB splits by ABI, so a device downloads exactly one `.so`; if distribution ever moves off Play to direct APKs, that number becomes real and this decision needs revisiting.

The trade is real weight against a guarantee. Bundled means scanning works on first launch, offline, on any device, with no dependency on Google Play Services being present and healthy, and **no "getting ready, connect once" state to design, build, test or explain**. The downloaded variant keeps the app at ~5.4 MB and would work in almost every real case — the model arrives at install time and is shared across apps — but it purchases the size saving by introducing a failure mode into a feature the app otherwise promises works offline.

The relative framing ("doubles the app") was rejected as misleading during the grill: ~~9 MB is not a size any user declines~~ — corrected below, the measured size is still small in absolute terms against the app-store norm, and the offline-first stance the app commits to everywhere else is worth more than the size.

Cloud OCR was rejected outright — per-call cost, a network requirement, and receipt images leaving the device, all for a feature that must work in a grocery queue on bad signal.

**Revisit trigger fired and resolved (captain, 2026-08-04).** This ADR's own Consequences named ~9 MB as the point at which "decision 1 is the thing to revisit, not the feature." Measured size lands at roughly **1.9× that ceiling on arm64** and **1.45× on armeabi-v7a** — the trigger fired, hard, on both delivered ABIs. Put to the captain directly, with the Play-Services-delivered alternative re-priced against the real number (its rejection above was argued against 4 MB, not 12) and an on-demand feature-module option raised and declined without pricing (dynamic-module complexity, and still fails the first scan offline). **Decision: keep the bundled engine, size overrun accepted** — full reasoning in `data/ipon-love-receipt-gaps/decision-apk-size-trigger-fired.md`. Accepted knowing the cost lands on every free user too, not just paying ones: decision 6 below is now reversed and scanning is fully paywalled, so every free install carries ~12 MB (arm64) / ~8 MB (armeabi-v7a) of OCR engine it can never use. **This revisit trigger is now spent** — it should read as resolved, not reopened, on this number; a future trigger needs new evidence (a leaner artifact, or distribution moving off Play).

### 2. Camera capture arrives with **no new camera permission** — and that must survive future edits

Capture uses `ActivityResultContracts.TakePicture` with a `FileProvider` output URI. **`android.permission.CAMERA` is deliberately never declared.** `ACTION_IMAGE_CAPTURE` requires no runtime permission *unless the app declares CAMERA* — declaring it is what activates the requirement. Adding that line "for correctness" would introduce a permission prompt this design specifically avoids, and would do so silently, since the flow still works after the user grants it.

The app's permission list stays: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `USE_BIOMETRIC` — **plus** the one addition decision 7 forces, and nothing else.

### 3. Two doors, one capture path — and the Records door is a scrollable action wheel

Both entry points ship:

- ~~**A "Scan a receipt" CTA at the very top of the New transaction form**, above the Type row. It cannot be missed, requires no nav-graph change, and works no matter how the editor was reached.~~ **Changed 2026-08-04** (captain, from a visual walkthrough of the flow — `decision-flow-confirmations.md`). The form entry is **two buttons side by side at the very top of the New transaction form**, above the Type row: `📷 Scan receipt` (camera) and `🖼️ From gallery` (picker) — each a single tap, with the intermediate camera-or-gallery chooser sheet removed entirely. This takes Slice 1's flow from four steps to three. Rationale: one less tap, and both routes are named. The rejected alternative was a single split control with the gallery leg demoted to a small icon — it saved no meaningful height (both shapes are one row) and left the gallery route unlabelled, which is the wrong trade because the gallery leg carries the GCash/Maya/GrabPay screenshot case, the cleanest and most reliable read this app's receipts get, not a route to bury. **Both buttons are premium-gated** (decision 6, reversed below) — the gallery leg is the same feature from a different source, so gating only the camera button would leave a free bypass.
- **A scrollable FAB wheel on Records.** One FAB position; the armed action sits large in the standard spot with the others peeking smaller above it. Swiping vertically moves a different action into the armed slot, growing it. Ships with two — `＋` (new transaction) and `📷` (scan).

Three rules govern the wheel, all settled at the grill:

- **It always resets to `＋`.** The armed slot never persists across visits. Predictability beats the saved swipe: a big button that changes meaning between visits makes a half-glance tap fire the wrong thing, and typing remains the common case. This also means **no stored preference** — nothing in DataStore, no per-device/per-account question.
- **The small buttons are directly tappable.** Tapping the small `📷` opens the camera without swiping first. This is what rescues the wheel's discoverability problem: a user who never realises it scrolls can still reach the camera in one tap, so the swipe is polish rather than a toll gate.
- **It is built generic over a list of actions**, because a third slot (🎤 voice) is expected when [Item 3](../build/v1.7.3.md#item-3--voice-command-natural-language-voice-input-to-log-a-transaction) un-defers. Adding it must be an entry in a list, not a rewrite — per the standing "build the general facility, not the use-case feature" rule.

A one-time coach mark anchors on the wheel. This is precisely [ADR-0059](0059-coach-mark-coverage-is-discoverability-triage.md)'s bar — a step exists only where the interaction **isn't inferable from looking at the screen**, and a FAB that scrolls is not inferable.

~~The scan action offers **camera or gallery**, not camera alone.~~ Both entry points still resolve to camera or gallery, not camera alone — the New transaction form via its two direct buttons above (no chooser), the FAB wheel unchanged per its own rules above. The gallery leg reuses the picker already in the code and covers **GCash/Maya/GrabPay confirmation screenshots** — high-contrast, perfectly legible, and plausibly a larger share of this audience's spending than paper receipts.

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

### 6. Scanning is gated — premium only (**REVERSED 2026-08-03, captain's ruling**)

~~Scanning is **not gated**. The existing receipt-photo cap is the premium differential.~~ Superseded reasoning kept below for the record — it no longer governs.

~~**No new `Feature` enum entry.** Scanning a receipt is recording your own money — just faster — and [D5](../build/subscription-paywall-design.md) is explicit that *"recording your own money is never gated … the wall is around power and polish, never around the core ledger."*~~

~~The premium differential arrives for free from finding 3: on the same scan, a premium user's photo is **kept as an attachment**, a free user's is **read and dropped**. This is structurally the [Export decision](../build/v1.7.0.md#item-6--general-export-facility-csv--pdf--zip-receipt-photos-included) repeated — CSV stayed free because trapping a user's data reads badly against D5, and the gate landed on the photo-bundling formats instead, "non-cannibalising by construction" because free has no photos to bundle.~~

~~The honest case against was heard and rejected: this is plausibly the strongest single reason anyone would pay ₱249, and `CALCULATOR` — a much smaller feature — is gated. It loses because Calculator is a side tool and the recurring calendar is a *view*, whereas scanning is a **method of entering transactions**, which is the one thing D5 names as never wallable. Manual entry remains untouched and fully capable, so nothing is blocked; only the kept photo differs.~~

~~A monthly free-scan allowance was also rejected — the paywall design states *"No free trial — the generous free tier **is** the trial"*, and a counter would need storing, resetting and syncing for a soft limit.~~

**Decision, 2026-08-03** (`data/ipon-love-receipt-gaps/decision-free-tier-photo-drop-copy.md`). Receipt scanning — both entry buttons (decision 3) — is **fully paywalled**. The governing reading is no longer "recording your own money is never gated"; it is the one [v1.7.3.md Item 3](../build/v1.7.3.md#item-3--voice-command-natural-language-voice-input-to-log-a-transaction) already states for voice: **this is convenience layered on recording, not recording itself.** The "honest case against" heard and rejected in the struck-through reasoning above — that scanning is plausibly the strongest single reason anyone pays ₱249 — is what carried the reversal: it stopped being a case *against* gating and became the case *for* it.

Consequences of the reversal:

- **A new `Feature` entry is needed** in the entitlement system — the `Feature` enum plus a `PremiumGate` check at both scan entry points and paywall routing — the thing this decision, as originally written, explicitly said would not be needed. Not built here: this ADR describes the gate, [subscription-paywall-design.md](../build/subscription-paywall-design.md) §8 records it in the feature map, and the actual enum entry is a build-time task for Slice 1.
- **`PlanLimits.maxReceiptPhotos` is no longer the mechanism that differentiates scanning.** It still governs manually attached receipt photos (the existing `ReceiptStrip` attach path) unchanged; it has nothing to do with the scan flow any more, because a free user is blocked before a scan ever produces a photo to cap.
- **The free-tier photo-drop moment this decision originally designed around no longer exists.** A free user never reaches a successful scan, so there is no "photo read, then dropped" beat and no copy is needed for it.
- **The gate ships dormant**, per the standing build stance (`AppConfig.DORMANT`, `enforcementEnabled = false`) — every user can still scan on today's builds until the enforcement flip.
- **Slice 1's on-device check changes**: force enforcement ON and verify a free entitlement is blocked *at the scan CTA* (both buttons), not a photo-drop caption that no longer exists — see [v1.7.3.md](../build/v1.7.3.md) Item 2's Verify section.

### 7. The photo has two destinations with different rules

- **In-app attachment** — subject to `maxReceiptPhotos` via the existing `CheckReceiptPhotoCapUseCase` path. This is the premium-differentiated copy: it syncs, renders on the transaction, appears in the partner combined view, and bundles into a PDF/ZIP export.
- **Gallery copy** — written to a dedicated **`Pictures/Love, Ipon`** album, never dumped into the camera roll root. **Free for everyone and never gated**: it costs the app nothing (no Storage egress, no sync, no cap), and it is the user's own photo on their own phone.

The gallery copy is the **one thing that forces a manifest change**. `MediaStore` writes need no permission on API 29+, but `minSdk = 26` still supports Android 8.0/9.0, where they need `WRITE_EXTERNAL_STORAGE`. Alvin's call: **declare it with `android:maxSdkVersion="28"` and request it at runtime only on those versions**, so every user gets the gallery copy, rather than silently degrading on old phones. This is the sole permission added by the whole feature; decision 2's no-CAMERA property is unaffected.

Gallery saving is **a Settings → Finance toggle, default ON**. The switch exists because gallery photos sync to Google Photos, so a user's grocery and pharmacy receipts land in a backed-up, possibly shared camera roll — that should be a choice, not a surprise.

**Timing, pinned 2026-08-03** — previously unanswered (`data/ipon-love-receipt-gaps/report.md` §4.1, gap 1 of the second review). The gallery copy is written **on Save, never at capture** — in `AddTransactionViewModel.save()`, after the transaction images are persisted and before the draft is cleared, gated on the toggle. Writing at capture would pollute the gallery with abandoned scans the moment a user backs out of an unsaved draft, and does so worse than the [v1.7.0 Item 14](../build/v1.7.0.md#item-14--orphaned-receipt-files-are-never-cleaned-up-device-storage-leak) leak it would repeat: a `Pictures/Love, Ipon` file sits outside every sweep the app owns (`CleanupOrphanedReceiptsUseCase` sweeps only `filesDir/receipts`; `ReceiptScanFileStore.sweep()` below sweeps only `cacheDir/scans`) and may already be synced to Google Photos before any cleanup could run. **Source: the full-resolution `cacheDir/scans` temp file, not the compressed 1080px/JPEG-85 copy** — handing the user a downgraded re-encode of a photo they believe they took would be a silent quality loss against this decision's own framing ("the user's own photo on their own phone"). **Camera leg only** — a gallery-picked image is already in the gallery, so writing a second copy there is pure duplication; the gallery-copy write is skipped entirely on the picker leg.

### 8. A failed read stays in the camera

Nothing readable → **"Couldn't read that one"** with **Retake** and **Enter manually**. A failed scan is nearly always a bad frame (glare, angle, motion), and retaking is both the actual fix and one tap away without navigating anywhere. Falling through to a blank form was rejected: it makes typing the path of least resistance precisely when a second photo would have worked.

A **partial** read is not a failure — whatever was found fills in, the rest stays blank, and the form opens. Only a completely empty result triggers the retake prompt.

### 9. Temp-file ownership (added 2026-08-03 — gap 2 of the second review, its highest-value finding)

Nothing in this ADR as originally written owned the cleanup of the full-resolution capture between `TakePicture` and compression — re-introducing the exact bug class [v1.7.0 Item 14](../build/v1.7.0.md#item-14--orphaned-receipt-files-are-never-cleaned-up-device-storage-leak) (`161acec`) already paid to fix. `CleanupOrphanedReceiptsUseCase` cannot be extended to cover it: it reads only `filesDir/receipts` ([CleanupOrphanedReceiptsUseCase.kt:20](../../app/src/main/java/com/iponlove/app/feature/transactions/domain/usecase/CleanupOrphanedReceiptsUseCase.kt#L20)), decides orphan-hood by `file.nameWithoutExtension ∈ transaction_images.id` (`:23`, `:32` — a full-res temp sharing its compressed sibling's id would read as "known" and never be swept, actively protecting the leak), and the two files have different lifetimes (durable-until-referenced vs. minutes-at-most).

**Decision — a distinct lifecycle, `cacheDir/scans/{uuid}.jpg`, modelled on `ExportFileWriter` ([ExportFileWriter.kt:16-46](../../app/src/main/java/com/iponlove/app/feature/export/data/ExportFileWriter.kt#L16-L46)), with one deliberate deviation:**

- New `ReceiptScanFileStore`: `newCapture()` → `cacheDir/scans/{uuid}.jpg`, deleting any existing file first (mirrors `ExportFileWriter.newFile`'s delete-if-exists); `uriFor()` → the existing `FileProvider` (`AndroidManifest.xml:66-75`, already `grantUriPermissions="true"`), plus one new line in `file_paths.xml`: `<cache-path name="scans" path="scans/" />`.
- **`sweep()` deletes only files older than one hour — not everything in the directory, unlike `ExportFileWriter.sweep()`.** Copying that sweep verbatim ships a new bug: `ACTION_IMAGE_CAPTURE` hands off to a separate camera process, and on a low-RAM device — this app's PH budget-Android market, including the maintainer's own test device — the app's process is routinely killed while the camera is foreground. `ActivityResultRegistry` persists and redelivers the pending result across that restart, but an unconditional sweep at `IponApp.onCreate` fires on exactly that restart and deletes the in-flight capture before the redelivered result is read — surfacing as intermittent "Couldn't read that one" on precisely the devices most likely to hit it. An age threshold removes the failure mode entirely: an in-flight capture is seconds-to-minutes old, an abandoned one is found at the next cold start after that.
- **Three owners delete it**, mirroring Item 14's two-halves shape plus one: (1) `AddTransactionViewModel`, immediately after recognise + compress succeed, unless the gallery-copy toggle is ON (then decision 7's Save-time write holds it); (2) `AddTransactionViewModel`, on retake, before the next capture is created; (3) `ReceiptScanFileStore.sweep()` at app start, the backstop for process death and hard kills.
- **The temp path survives process death** via `SavedStateHandle`, alongside the rest of the draft ([AddTransactionViewModel.kt:379-381](../../app/src/main/java/com/iponlove/app/feature/transactions/presentation/AddTransactionViewModel.kt#L379-L381)) — without it, a restored draft cannot find its own capture and silently fails decision 7's gallery-copy write.

### 10. EXIF handling (added 2026-08-03 — gap 6 of the second review)

**Recognition: free.** Build the ML Kit input with `InputImage.fromFilePath(context, uri)`, not `BitmapFactory.decodeStream` + `InputImage.fromBitmap(bitmap, 0)` — the latter is the silent-garbage path this gap names, since ML Kit reads and applies EXIF orientation itself inside `fromFilePath`, while `fromBitmap` requires the caller to supply degrees explicitly.

**Storage: a live pre-existing bug, not new surface.** The app has no EXIF handling anywhere today (`grep -rn "ExifInterface\|exif" app/src/main app/build.gradle.kts gradle/libs.versions.toml` — zero hits). [CompressReceiptUseCase.kt:25-27](../../app/src/main/java/com/iponlove/app/feature/transactions/domain/usecase/CompressReceiptUseCase.kt#L25-L27) decodes with `BitmapFactory.decodeStream` (ignores EXIF) and re-encodes with `Bitmap.compress` (`:32`, writes no EXIF), so a source image carrying an orientation tag is stored sideways with the tag stripped. Latent today because gallery-picked photos usually arrive pre-rotated — camera captures almost always carry an orientation tag, so Slice 1 makes this routine rather than rare. `AddNoteImageUseCase.kt:29-41` is a verbatim copy of the same defect.

**Fix in Slice 1**, in `CompressReceiptUseCase`: read `ExifInterface(TAG_ORIENTATION)` and apply a `Matrix` rotation before `compress`. `androidx.exifinterface:1.3.7` arrives transitively with ML Kit's `vision-common` — no new declared dependency. Fixing `AddNoteImageUseCase`'s identical bug in the same slice is a scope call, not required by this ADR.

## Consequences

- **No schema change, no Room change (stays v31), no Supabase migration**, and **no impact on [Item 1](../build/v1.7.4.md#item-1--freeze-the-cross-platform-contract-from-web-phase-0-w2)'s cross-platform contract freeze** — the parser is client-local, mints no deterministic ids, and writes only through existing synced columns.
- **Web parity is upload-only — and the engine, not the camera, is the real gap** (Alvin, 2026-07-29). The web client takes a *picked* image only; no camera capture. That leg costs little: browsers can in fact open a camera (`<input type="file" accept="image/*" capture>`, or `getUserMedia`), so upload-only is a **scope decision, not a platform limit** — and decision 3's gallery leg, the GCash/Maya screenshot case, is the one that ports cleanly and is plausibly the larger share of use anyway. The load-bearing gap is **decision 1**: there is no ML Kit on web, so a web scan needs either a wasm engine (Tesseract.js — materially worse on thermal print) or a cloud OCR call. ~~A per-call cost would reopen decision 6's ungated ruling on web only, since "costs nothing per use" is exactly what made not gating it free.~~ **Moot as of decision 6's reversal (2026-08-03)** — the feature is gated on Android regardless of engine cost now, so a web cloud-OCR call would land inside an already-gated feature rather than forcing a new gating decision. Decision 7's `Pictures/Love, Ipon` copy has no web analogue at all. None of this changes the Android design — it means the web build must pick its own engine and **cannot inherit this ADR wholesale**.
- ~~**APK grows ~4 MB.** Measure at build; if it lands materially above ~9 MB, decision 1 is the thing to revisit, not the feature.~~ **Measured and resolved 2026-08-04** — see decision 1 above. Delivered size is arm64-v8a 17.14 MiB / armeabi-v7a 13.06 MiB (+11.90 / +7.82 MiB); the ~9 MB trigger fired and the captain's call (keep bundled, accept the size) is recorded there. This bullet is spent — do not re-derive the trigger from the stale ~4 MB estimate.
- **A future edit that adds `<uses-permission android:name="android.permission.CAMERA" />` silently regresses decision 2** by introducing a runtime prompt where there was none. The manifest carries a comment saying so, in the style of the existing `allowBackup` note.
- **`WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) appears in the Play listing.** Accepted deliberately over silently dropping the gallery copy on Android 8/9.
- **The feature is weakest on a fresh account** — no history means no category or account inference until the user has scanned or logged a given merchant once. This is inherent to decision 5 and should shape the copy, not be papered over.
- **A wrong inferred `Account` corrupts a balance**, where a wrong `Category` only misfiles a chart. This asymmetry is why decision 4 marks both inferred fields rather than letting them arrive silently.
- **One receipt = one transaction, permanently — not an overlooked gap, a deliberate boundary** (confirmed 2026-08-03, gap 9 of the second review). The editor is single-amount/single-category by construction (`TransactionEditorState`, one `amountText`, one `categoryId`, validated as a unit) and so is the row (`supabase/schema.sql:167-168` — one `amount numeric(14,2) not null`, one `category_id`). A future line-item split is not a small addition: it is either **N transactions from one scan** (multiplying the dedup problem and the history-inference problem by N, with no app concept of "undo this scan" as a multi-row operation) or **a new line-item child table**, either of which breaks this ADR's own "no schema change … no impact on the contract freeze" claim above. **Reversing this later is a schema change plus a cross-platform-contract-freeze interaction**, not a follow-on slice.
- **The duplicate-scan warning is Slice 2, not Slice 1, and it warns — it never blocks.** Scanning the same receipt twice today produces two identical transactions with no warning; the fix (an inline banner on the review form when a same-type/same-amount/±1-day transaction already exists) ships alongside Slice 2's history inference, since both need the same "read my own past transactions" plumbing. Save stays fully enabled even when the banner fires — a legitimate second same-day expense (e.g. two jeepney fares) is common, and a false positive that refuses Save is worse than the duplicate it would prevent.
- **Three slices, verified on-device between each** (per the house rule): (1) scan → filled form, including camera/gallery, reading, parsing, review, retake, attach, gallery save and its setting; (2) history inference for category + account with their captions, plus the duplicate-scan warning above; (3) the FAB wheel and its coach mark. Slice 1 alone is the feature end-to-end.

## Rejected alternatives (summary)

| Alternative | Why rejected |
|---|---|
| Converge with the existing `ReceiptStrip` attach flow | It sits last on the form — found only after the user has typed everything it would have filled |
| Play-Services-delivered OCR model | ~~Saves ~4 MB~~ — re-priced 2026-08-04 against the measured ~12 MB (arm64) delta and still rejected; buys a "getting ready" failure state and a Play Services dependency into an offline-first feature |
| On-demand feature module (engine downloads on first premium scan) | Raised 2026-08-04 alongside the size-trigger decision, declined without pricing — spares free users the download but adds dynamic-module complexity and still fails the first scan without network |
| Cloud OCR API | Per-call cost, network requirement, receipt images leaving the device |
| LLM parsing of the recognised text | Inherits Item 3's cost/device problem to solve what regex plus a history lookup already solve |
| Hardcoded PH merchant → category table | Knows only what's hardcoded, misses regional and sari-sari stores, needs maintaining forever |
| Auto-arm the last-used FAB action | The big button would change meaning between visits, so a half-glance tap fires the wrong thing |
| Single split control for the form entry (gallery demoted to a small icon) | Rejected 2026-08-04 — saved no meaningful height over two full-width buttons and left the gallery route unlabelled, wrong because that leg carries the cleanest, most reliable read (GCash/Maya screenshots) |
| ~~A `RECEIPT_SCAN` premium gate~~ | ~~Walls off a *method of recording own money*, which D5 names as never wallable; the photo cap already differentiates~~ — **superseded 2026-08-03: this is now the adopted design.** See decision 6, reversed. |
| Free monthly scan allowance | Contradicts "the generous free tier **is** the trial"; a synced counter for a soft limit |
| Cropping / edge detection | Retake is the fix for a bad frame, at a fraction of the complexity |
| Blank form on a failed read | Makes typing the path of least resistance exactly when a second photo would have worked |
| Line-item split (one receipt → many transactions) | Structural, not a nice-to-have — breaks the single-amount/single-category editor and row shape (decision 4) and reopens the contract freeze; see Consequences |
