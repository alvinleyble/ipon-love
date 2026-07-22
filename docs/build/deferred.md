# Deferred items (cross-version catch-all)

**Purpose:** items that are booked (a real, scoped ask) but intentionally **not scheduled** — either blocked on external input (tester feedback, a decision from Alvin) or not yet designed (needs a grill before it can be booked to build). Kept out of the active version doc so that doc's "what's left" list stays limited to work actually in flight. When a deferred item is picked back up, move its section back into the current version doc (or link back here from the item that unblocks it) and update its Status line.

Each item below keeps the version doc it was originally booked under, for traceability.

---

## Item 40 — Payday / bill due-date reminder notification (needs grill)

*(originally booked in [v1.6.6.md](v1.6.6.md), split out of Item 7 2026-07-17)*

- **Status:** ⏸️ **DEFERRED — not designed, needs a grill before booking to build.** Moved here 2026-07-17 (Alvin: unnecessary for now). **Model (once grilled): TBD** — likely Opus, since it's cross-cutting with the sync architecture, not a pattern-follow.
- **Origin:** [v1.6.6.md Item 37](v1.6.6.md#item-37--confirm-on-arrival-recurring--incomebill-forecasting) Q14 — Alvin's payday-visibility request surfaced a "should there be a reminder notification" question that the grill deliberately deferred rather than resolve inline.
- **The open design tension:** a scheduled due-date reminder needs new local-notification infra (likely `AlarmManager`/`WorkManager`-based date scheduling) — but the app's whole sync/background posture is deliberately foreground-only (interactive sync runs in-process; ADR-0012; v1.6.6 Item 36 just declined periodic background sync for the balance widget on the same philosophy). Whether a payday reminder is worth the exception, and if so what mechanism respects it (e.g. a lightweight local `AlarmManager` alarm that doesn't touch sync/network, vs. WorkManager periodic), is unresolved.
- **Not a blocker for anything** — the Records "To confirm" card ([v1.6.6.md Item 37](v1.6.6.md) Slice 1, shipped) already surfaces pending occurrences on next app open without this.

---

## Item 41 — Play Store screenshot refresh (Playful Pop reskin)

*(originally booked in [v1.6.8.md](v1.6.8.md), carried to [v1.6.9.md](v1.6.9.md), moved here 2026-07-22)*

- **Status:** ⏸️ **DEFERRED — do at prod release.** Moved here 2026-07-22 (Alvin: hold the re-render until we're releasing into prod). Not blocked on a decision — deliberately timed: more reskin/UI churn is still expected ([[playstore-screenshots-in-flux]]), and the store screenshots should capture the *final* shipping UI rather than be re-shot every batch. **Model: TBD** — booked as Fable 5 (visual/brand-taste fit), but Alvin is weighing Opus instead, since on Claude Pro Fable spends separate credits while Opus rides the session tokens already being paid for; decide at pickup.
- **Request:** Alvin — "since we reskinned, we'll need to update our screenshots in playstore." The v1.6.7 Item 8 "Playful Pop" redesign (`f570463`+the full Slice 6 rollout, [v1.6.7.md](v1.6.7.md) Item 8) changed the visual identity of every screen since the last screenshot set was rendered (v1.6.5 Item 22, `43f59c1`, Rose-dark captures on the pre-reskin M3 look).
- **Change (when picked up):** re-run the existing pipeline in [assets/brand/playstore/](../../assets/brand/playstore/) — drop fresh raw captures (Rose, light+dark per the existing convention) into `assets/brand/phone-screenshots/` from the reskinned app, remap slide→screenshot filenames in `slides.css` as needed, re-render via `render.sh` (recipe in `assets/brand/README.md`). Reuses the same 8-slide Heart-Wallet brand-voice design system — no new slides, no copy changes, just re-capturing the underlying screens. Republish to the same Artifact gallery path for review.
- **Verify:** 8 Play-compliant 1080×1920 images regenerated, reviewed via Artifact gallery; Play Console upload stays Alvin's manual step (repo assets only, no app code/tests affected).

---

## Item 4 — Custom fonts (pulled from Post-V1 Horizon #8)

*(originally booked in [v1.6.7.md](v1.6.7.md), held in [v1.6.8.md](v1.6.8.md), carried to [v1.6.9.md](v1.6.9.md), moved here 2026-07-22)*

- **Status:** ⏸️ **DEFERRED — not designed, needs a grill before booking to build.** Moved here 2026-07-22 (Alvin's call). **Model (once grilled): TBD** — likely Opus, since Horizon #8 was explicitly designed to be "born gated" behind the paywall infra (#15), which has since shipped dormant; wiring a new customization axis into the entitlement/`PlanLimits` seam (the S9 boolean/allowlist-gate pattern) is a cross-cutting call, not a pure pattern-follow.
- **Origin:** [Post-V1 Horizon #8](project-build-progress.md) — "typography customization beyond the built-in color themes" (Target: Q3 2026). Pulled into active consideration at Alvin's call, 2026-07-18 — "we will figure out if this will either be retained or built" — then deferred back to the shelf 2026-07-22 without that question being resolved.
- **Open question to resolve first, at the grill:** is this still wanted right now, or does it go back on the shelf for good? **If retained:** font source (bundled font files vs. system font picker), where it plugs into the theme system (a `ThemeFont` sibling to `ThemePalette`?), the free-vs-premium split (which fonts if any stay free, mirroring the S9 palette allowlist), and whether it needs a new `Feature` gate or extends existing `PlanLimits`.
- **Not yet designed** — grill to resolve retain-vs-build first, then the shape if retained.

---

## Item 23 — Archived entity label fallback in Records & Budgets (✅ RESOLVED by v1.6.7 Item 5)

*(originally booked in [v1.6.5.md](v1.6.5.md), carried to [v1.6.6.md](v1.6.6.md), moved here 2026-07-17)*

- **Status:** ✅ **RESOLVED `b05087d`** (2026-07-22) — the [v1.6.7 Item 5](v1.6.7.md#item-5--archive-label-preservation--delete-confirm-that-steers-to-archive-resolves-deferred-item-23) grill **overturned the "needs tester feedback" premise**: archiving is the non-destructive twin of delete, so it should *never* degrade a label. Records + Budgets now feed their display name-maps from `observeCategories/Accounts(includeArchived = true)`, so a historical row pointing at an archived entity keeps its real label everywhere (Analysis already did). The label-degrading footgun moved onto **Delete**, which Item 5 gates behind an archive-steering confirm. No tester-feedback wait needed. *(Historical write-up preserved below.)*
- **Superseded status:** ⏸️ ~~DEFERRED — booked but intentionally not scheduled; Alvin wants tester feedback first before deciding whether it's a bug to fix or the intended signal.~~ Split out of [v1.6.5.md Item 19](v1.6.5.md#item-19--manage-surface-archived-accountscategories-so-they-can-be-unarchived-) to keep that slice tightly scoped. **Model: Sonnet, low effort** (built as part of Item 5). Non-paywall.
- **Independent** — pure display-time label resolution, no schema/sync/data change.
- **Related item booked 2026-07-18:** [v1.6.7.md Item 5](v1.6.7.md#item-5--archived-category-confirm-on-archive-warning-related-to-deferred-item-23) tackles the *archive-time* warning UX for the same root problem — this item stays deferred/unscheduled regardless (still waiting on tester feedback for the display-time question), but the two are related and the Item 5 grill may end up resolving this one too.

**Surfaced 2026-07-11** while confirming Item 19's archive semantics. Records and Budgets resolve a category/account **name from its id** via a lookup map built from `observeCategories()` / `observeAccounts()` at the default `includeArchived = false`. So a **historical row pointing at a now-archived entity** drops out of the map and falls back to a generic label:
- **Records list** ([TransactionsViewModel.toListItem](../../app/src/main/java/com/iponlove/app/feature/transactions/presentation/TransactionsViewModel.kt#L161)) — a transaction whose **category** was archived reads **"Uncategorized"** (`categoryNames[categoryId] ?: "Uncategorized"`); whose **account** was archived reads **"Account"** (`accountNames[accountId] ?: "Account"`).
- **Budgets** ([BudgetsViewModel](../../app/src/main/java/com/iponlove/app/feature/budgets/presentation/BudgetsViewModel.kt#L78)) — an existing budget on a now-archived category reads **"Category"** (`categoryNames[it] ?: "Category"`); the budget still tracks spending correctly, only the name degrades.

**The inconsistency:** [Analysis](../../app/src/main/java/com/iponlove/app/feature/analysis/presentation/AnalysisViewModel.kt#L83) deliberately observes `includeArchived = true` so archived categories **keep their label** in the donut/flow; Recurring materialization does the same. Records and Budgets never got that treatment, so the **same archived category is labeled three different ways** depending on the screen. It is **purely cosmetic** — no data loss (the row still carries its `categoryId`/`accountId`), and **unarchiving restores every label instantly** (the entity re-enters the map).

**The product question to answer with tester feedback (why it's deferred, not just built):** is an archived category *supposed* to keep naming its old transactions in Records (consistent with Analysis), or is **"Uncategorized"** the intended cue that "this category is retired"? Don't change the behavior until this is decided.

**If built (the shape):** Records is a one-line switch to `observeCategories(includeArchived = true)` / `observeAccounts(includeArchived = true)` (it uses the list only for label lookup, not a picker). Budgets is a **two-line split** — build the label map from an archived-inclusive observe, but keep the **new-budget picker** (`expenseCategories = categories.filter { it.type == EXPENSE }`) filtered to **active** categories so archived ones don't reappear as budgetable options. (The Add-Transaction / Quick-add / Recurring-editor pickers stay `false` — archived items must not be offered for *new* activity.)

**Verify (if built):** archive a category with existing transactions + a budget → Records rows keep the real name (not "Uncategorized"), the budget row keeps its name (not "Category"), Analysis unchanged; the archived category does **not** appear in the Add-Transaction picker or the new-budget picker; unarchive → everything unchanged.

---

## Shelved — no active interest (indefinite)

Distinct from the deferred items above: these aren't blocked on a decision or waiting on feedback — Alvin has explicitly said there's no active interest in building them right now. Kept here (not deleted) so the reasoning isn't lost if a concrete need ever surfaces. Not on the tester-facing Upcoming-features screen.

### Password vault

*(originally [Post-V1 Horizon #4](project-build-progress.md), moved here 2026-07-18)*

- **Status:** 🗄️ **SHELVED — no active interest, moved 2026-07-18** (previously "not yet determined — no active design or demand signal yet" on the Horizon list; Alvin's call during a 2026-07-18 backlog triage was to move it here explicitly rather than leave it lingering on the Horizon list).
- **Shape (if ever revisited):** new `feature/vault` module, SQLCipher/EncryptedDataStore, per the CLAUDE.md scalability principle ("Encryption utilities in `core/`, not buried inside `feature/vault/`"). Never designed past that one-line sketch.

### CSV / PDF export

*(originally [Post-V1 Horizon #7](project-build-progress.md), shelved 2026-07-07, write-up moved here from [v1.6.5.md](v1.6.5.md) 2026-07-18)*

- **Status:** 🗄️ **SHELVED — deferred indefinitely 2026-07-07** at Alvin's call ("I don't think I need it, put it on the shelf for now"). Already out of V1 scope (CLAUDE.md). Not on the Upcoming-features teaser. Revisit only if a concrete need surfaces.
- **Shape (if ever revisited):** data is already structured for it — transactions/budgets/analysis are all queryable Room tables; would be a straight export UseCase over existing repositories, per the CLAUDE.md scalability principle ("UseCases own data access... a future export UseCase must be able to reuse them").

### Facebook Login

*(originally [Post-V1 Horizon #2](project-build-progress.md), moved here 2026-07-23)*

- **Status:** 🗄️ **SHELVED — deferred indefinitely 2026-07-23** at Alvin's call, decided alongside booking Google Sign-In ([v1.7.0.md Item 2](v1.7.0.md#item-2--google-sign-in-pulled-from-post-v1-horizon-1)) — Google Sign-In alone covers the SSO ask for now; no active interest in a second OAuth provider. Already out of V1 scope (CLAUDE.md — "Explicitly Out of Scope for V1: Google / Facebook SSO"). Removed from the Upcoming-features teaser ([v1.7.0.md Item 3](v1.7.0.md#item-3--facebook-login-shelved-indefinitely)).
- **Shape (if ever revisited):** Supabase OAuth + Facebook SDK, most naturally following whatever OAuth plumbing Google Sign-In establishes (deep-link callback handling, account-linking rules for an existing email/password user). Never designed past that one-line sketch.
