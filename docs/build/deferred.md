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

## Item 23 — Archived entity label fallback in Records & Budgets (⏸️ DEFERRED — needs tester feedback)

*(originally booked in [v1.6.5.md](v1.6.5.md), carried to [v1.6.6.md](v1.6.6.md), moved here 2026-07-17)*

- **Status:** ⏸️ **DEFERRED** — booked but intentionally **not scheduled**; Alvin wants **tester feedback first** before deciding whether it's a bug to fix or the intended signal. Split out of [v1.6.5.md Item 19](v1.6.5.md#item-19--manage-surface-archived-accountscategories-so-they-can-be-unarchived-) to keep that slice tightly scoped. **Model (if built): Sonnet, low effort.** Non-paywall.
- **Independent** — pure display-time label resolution, no schema/sync/data change.

**Surfaced 2026-07-11** while confirming Item 19's archive semantics. Records and Budgets resolve a category/account **name from its id** via a lookup map built from `observeCategories()` / `observeAccounts()` at the default `includeArchived = false`. So a **historical row pointing at a now-archived entity** drops out of the map and falls back to a generic label:
- **Records list** ([TransactionsViewModel.toListItem](../../app/src/main/java/com/iponlove/app/feature/transactions/presentation/TransactionsViewModel.kt#L161)) — a transaction whose **category** was archived reads **"Uncategorized"** (`categoryNames[categoryId] ?: "Uncategorized"`); whose **account** was archived reads **"Account"** (`accountNames[accountId] ?: "Account"`).
- **Budgets** ([BudgetsViewModel](../../app/src/main/java/com/iponlove/app/feature/budgets/presentation/BudgetsViewModel.kt#L78)) — an existing budget on a now-archived category reads **"Category"** (`categoryNames[it] ?: "Category"`); the budget still tracks spending correctly, only the name degrades.

**The inconsistency:** [Analysis](../../app/src/main/java/com/iponlove/app/feature/analysis/presentation/AnalysisViewModel.kt#L83) deliberately observes `includeArchived = true` so archived categories **keep their label** in the donut/flow; Recurring materialization does the same. Records and Budgets never got that treatment, so the **same archived category is labeled three different ways** depending on the screen. It is **purely cosmetic** — no data loss (the row still carries its `categoryId`/`accountId`), and **unarchiving restores every label instantly** (the entity re-enters the map).

**The product question to answer with tester feedback (why it's deferred, not just built):** is an archived category *supposed* to keep naming its old transactions in Records (consistent with Analysis), or is **"Uncategorized"** the intended cue that "this category is retired"? Don't change the behavior until this is decided.

**If built (the shape):** Records is a one-line switch to `observeCategories(includeArchived = true)` / `observeAccounts(includeArchived = true)` (it uses the list only for label lookup, not a picker). Budgets is a **two-line split** — build the label map from an archived-inclusive observe, but keep the **new-budget picker** (`expenseCategories = categories.filter { it.type == EXPENSE }`) filtered to **active** categories so archived ones don't reappear as budgetable options. (The Add-Transaction / Quick-add / Recurring-editor pickers stay `false` — archived items must not be offered for *new* activity.)

**Verify (if built):** archive a category with existing transactions + a budget → Records rows keep the real name (not "Uncategorized"), the budget row keeps its name (not "Category"), Analysis unchanged; the archived category does **not** appear in the Add-Transaction picker or the new-budget picker; unarchive → everything unchanged.
