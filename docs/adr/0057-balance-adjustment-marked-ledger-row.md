# Adjusting a balance writes a marked ledger row, not an opening-balance edit

## Context

[ADR-0007](0007-derived-account-balance.md) made account balance derived (`opening_balance` + ledger) and named — but never built — the mechanism for reconciling it: *"manual 'adjust balance' becomes a special adjustment transaction, not a direct balance write."*

Four years of the app shipping without it means users have two workarounds, both bad. They edit `opening_balance` in the account editor (which retroactively rewrites every historical balance, and turns a field ADR-0007 assumed "almost never changes" into a hot, LWW-contended one), or they invent a fake transaction (which pollutes Analysis and Budgets with spending that never happened).

Alvin's framing (2026-07-28): a user looks at their bank app, sees Love, Ipon disagrees, and **wants to type the correct number** — no arithmetic, no deciding whether it's a +₱500 income or a −₱500 expense. The friction is the mental math, not the tapping.

## Decision

### 1. The correction is a real, dated ledger row — never an `opening_balance` nudge

The cheap alternative (silently nudge `opening_balance` by the delta) was evaluated first, because it needs zero schema change. **Rejected**: it breaks ADR-0007's own safety argument. `opening_balance` is safe to leave LWW-exposed *precisely because* it almost never changes; ship an Adjust button that writes it and it changes constantly, on two devices, with LWW silently picking a winner.

The decisive comparison is a shared account, where `accounts_couple` ([schema.sql:525](../../supabase/schema.sql#L525)) grants **both** partners full write access to the row:

> Both partners reconcile "BPI Joint" against the bank within the same hour, from different local ledgers (one hasn't pulled the other's ₱500 grocery expense yet).
> - **Nudge:** LWW keeps one write, discards the other. The balance is wrong and there is **no row, no date, no trace** — neither partner can reconstruct it.
> - **Row:** both corrections survive and the account is over by ₱500 — also wrong, but there are two dated rows in Records and either partner can delete one.

Neither is immune to concurrent reconciliation. One fails **diagnosably**, the other fails **silently**. In a finance app that is the whole argument. The nudge also retroactively rewrites every past balance, which is unobservable today (there is no balance-over-time chart) but is a landmine for any future net-worth history.

### 2. Marked with `is_adjustment` on the transaction, not a built-in category

Two existing mechanisms could carry "real money, but not spending" semantics. The tempting one is a built-in **"Balance adjustment" category** flagged `exclude_from_analysis` — combining the Transfer-fees pattern ([SaveTransferUseCase.kt:65](../../app/src/main/java/com/iponlove/app/feature/transactions/domain/usecase/SaveTransferUseCase.kt#L65)) with [ADR-0049](0049-exclude-from-analysis-category-flag.md). It needs **zero schema change and zero new exclusion code**, since [`AnalysisExclusion`](../../app/src/main/java/com/iponlove/app/feature/categories/domain/usecase/AnalysisExclusion.kt) already drops flagged categories at the Analysis/Budgets/Combined boundaries — roughly 3 files against 18.

**Rejected** on three concrete findings:

1. **It spends the free tier.** `PlanLimits.FREE.maxPersonalCategories = 10`, and [`CapCount`](../../app/src/main/java/com/iponlove/app/core/entitlement/CapCount.kt) counts *all non-deleted rows, archived included*. Adjustments need **two** categories (INCOME for up, EXPENSE for down — sign is derived from type, and amounts are validated positive), and "Transfer fees" already consumes a third. Three of ten free-tier categories go to plumbing the user never asked for, and the failure surfaces later, as a paywall block when they try to add a real category.
2. **The pattern it copies is already broken.** [`CategoryDao.getById`](../../app/src/main/java/com/iponlove/app/feature/categories/data/local/CategoryDao.kt#L36) does not filter `isDeleted`, so `ensureTransferFeeCategory()`'s `if (getCategory(id) == null)` guard treats a **tombstone** as a live row. Delete "Transfer fees" today and every subsequent fee is filed under a soft-deleted category. Inheriting that bug twice is unacceptable.
3. **It re-creates decision 1's rejected failure mode.** A user-pickable category whose effect is "make this invisible in Analysis" means a real ₱3,000 grocery run filed there silently vanishes from Analysis *and* the user's budget, with no warning.

The principled line between the two existing precedents: **ADR-0049 chose a *category* flag because reimbursables cluster into a coherent bucket the user curates and names. ADR-0019 #14 chose a *transaction* flag (`is_settlement`) because a settlement leg is system-generated and uncategorizable by nature.** A balance adjustment is unambiguously the second kind, so it takes `is_adjustment` on `transactions` — mirroring `is_settlement` line for line at every hop (Supabase column, `partner_transactions` view, Room entity + v30→**v31** AutoMigration, both DTOs, four mapper sites, the validator's category waiver, three label branches, five exclusion filters).

### 3. Reach: counts toward balance, visible in the ledger, absent from every spend figure

| Surface | Behaviour |
|---|---|
| Account balance · Net assets | **counts** — the entire point |
| Records list | **visible**, labelled `Balance adjustment`, no category icon |
| Exports | **included** (an export that omits rows can't reconcile against the app) |
| Analysis — donut, expense flow, daily net | **skipped** |
| Budgets | **skipped** — a correction must never eat a real budget |
| Combined couple view — **feed** | **visible**, with owner colour attribution |
| Combined couple view — **spend totals** | **skipped** |
| Projected net | **unchanged** — it never reads a balance |

The Combined split is load-bearing and was nearly got wrong. A correction on a shared account moves *both* partners' displayed balance ([`ObserveBalanceLedgerUseCase`](../../app/src/main/java/com/iponlove/app/feature/transactions/domain/usecase/ObserveBalanceLedgerUseCase.kt) sums every member's non-private rows, and ADR-0018 forces shared-account rows non-private), but Records is own-rows-only. Hiding adjustments from Combined entirely — the way ADR-0049 categories are filtered *before* the calculator runs ([CombinedViewModel.kt:112](../../app/src/main/java/com/iponlove/app/feature/couple/presentation/CombinedViewModel.kt#L112)) — would leave the partner watching a joint balance move with **nowhere in the app** explaining it: decision 1's rejected silent failure, returning through the couples path. So adjustments follow the **settlement** shape instead ([CombinedLedgerCalculator.kt:61 / :78](../../app/src/main/java/com/iponlove/app/feature/couple/domain/usecase/CombinedLedgerCalculator.kt#L61)) — in the feed, out of the totals.

### 4. Entry point: the account editor's money field *is* the balance

Tapping an account in Manage → Accounts opens the existing editor. Its money field is labelled **"Balance"**, pre-filled with the current derived figure; overwrite it and Save. On **create** the same field is the starting balance (identical, since the ledger is empty).

**The raw `opening_balance` edit is retired.** A separate "Adjust balance" action in the ⋮ menu was rejected for leaving the trap in place — the user who today fixes their balance by editing Opening balance would keep doing exactly that, because that field would still be sitting there looking like the answer. After creation the user never sees "opening balance" again; it becomes internal bookkeeping. The field accepts **negative** values (cards and overdrafts have genuinely negative balances), unlike every other money input in the app.

**Empty ledger ⇒ no row.** If the account has no transactions, the same input corrects `opening_balance` directly. Nothing to rewrite, nothing to diverge, and it avoids a junk `−₱9,000 Balance adjustment` for a fat-finger at setup. One transaction in, and it is always a row.

### 5. Always dated now; corrections never reach backward

The delta is computed against the balance **at that moment**, and the row is dated now with no date picker.

A user-pickable date was **rejected** as internally incoherent: "target balance" only means anything as of an instant. Backdate a ₱10,000 correction to June 30 and the app must compare against the balance *as of June 30*, after which every July transaction still applies on top — so the account lands somewhere the user never asked for, breaking the feature's one promise ("type what the bank shows and the app matches it"). The genuine underlying need (I know when I stopped recording accurately) is served by entering the missing transactions, not by a time-travelling correction. Consequence, accepted: adjusting changes **nothing** retroactively — last month's Analysis, budgets, and Combined view are untouched.

Because it is dated now, the row is a normal transaction afterward; a user who really wants to move it can edit its date in Records, where the consequences are visible and explicitly chosen.

### 6. The mark survives the editor — and the same fix is owed to settlements

Every Records row is tappable into the editor with no guard ([TransactionsScreen.kt:323](../../app/src/main/java/com/iponlove/app/feature/transactions/presentation/TransactionsScreen.kt#L323)), and [`TransactionEditorReducer.build()`](../../app/src/main/java/com/iponlove/app/feature/transactions/presentation/TransactionEditorReducer.kt#L111) rebuilds the `Transaction` from scratch **without** carrying `is_settlement` forward. This is a **live bug on shipped behaviour**: opening a debt-settlement row and saving strips its flag, and that repayment silently starts counting as real spending in Analysis and against budgets.

`is_adjustment` is therefore threaded through `TransactionEditorState` and back out of `build()`, and the **category picker is hidden** for marked rows (the validator waives `CATEGORY_REQUIRED`, so a shown-but-empty picker would read as an uncleavable error). Editing the amount is deliberately kept — fat-fingering ₱5,000 for ₱500 is repaired by opening the row and fixing the number, landing the balance exactly where a re-adjust would. The identical settlement fix is **booked separately** (v1.7.1 Item 14) rather than smuggled in here, since it touches the Partner Debt Tracker.

### 7. Privacy follows account ownership

`is_private = true` on a personal account, `false` on a shared one (ADR-0018 requires the latter, and the partner needs to see why jointly-held money moved). A partner has no business seeing you correct your own GCash balance; they do need to see a correction on money that is also theirs. Own private rows still count toward the owner's balance, so personal balances stay correct.

### 8. Free, unconditional

Not a `Feature` gate. Same call ADR-0049 decision 6 made: bookkeeping *accuracy* is never gated, because gating it leaves a free user's balances knowingly wrong — the "recording your own money is never gated" principle (ADR-0044).

## Consequences

Reconciling becomes a first-class, zero-arithmetic action, and the two workarounds it replaces both stop being reachable — `opening_balance` is no longer user-editable after creation, and there is no longer a reason to invent a fake transaction. The ledger stays the single source of truth for balance in every case, which is ADR-0007 finally delivered rather than merely asserted.

Costs, accepted: an 18-touch-point mechanical change (each with a working `is_settlement` line to copy); a new `is_adjustment` column crossing to the partner via a one-line `partner_transactions` view addition; and Room v30→v31. Concurrent reconciliation of a **shared** account by both partners still produces a wrong balance — deliberately, since the alternative was a wrong balance with no evidence. There is no online gate and no forced pre-sync: the dialog pre-fills the balance it is computing against, so a stale figure is visible before commit, and gating a non-destructive action on connectivity would break offline-first for a narrow window.

**Alvin's call (2026-07-28), against the recommendation:** the Records list shows the **label only** (`Balance adjustment · BPI · ₱500`), not the from→to. The auto-note `₱9,500 → ₱10,000` is still written to the row's `note`, just not appended to the list subtitle the way notes normally are — so it surfaces when the row is opened, and in the note column of a CSV export, while the list stays uncluttered. The couple feed renders titles only, so shared-account corrections stay tidy there too. The note is an ordinary editable field, so a user can replace it with their own reason.

## Rejected alternatives (summary)

- **Nudge `opening_balance` by the delta** — no schema change, but silently rewrites all past balances and turns ADR-0007's deliberately-cold field into an LWW-contended hot one; fails invisibly on shared accounts.
- **A built-in "Balance adjustment" category flagged `exclude_from_analysis`** — 6× cheaper, but consumes 2 of a free user's 10 categories, inherits the live `getById`-returns-tombstones bug, and ships a user-pickable "hide this spending" footgun.
- **Reusing `is_settlement`** — a single flag cannot carry two labels; Records, exports, and the couple feed would all call a correction a debt settlement.
- **A separate "Adjust balance" item in the ⋮ menu** — clearer in isolation, but leaves the Opening balance field in the editor still looking like the answer, so the friction survives the feature built to remove it.
- **A user-pickable adjustment date** — incoherent with target-balance input; the account lands on a figure the user never asked for.
- **Hiding adjustments from the Combined view entirely** (the ADR-0049 filter shape) — leaves a partner watching a joint balance move with no explanation anywhere.
- **An optional "why?" reason box in the Adjust flow** — one more thing to skip past on a screen whose entire selling point is "type the number and save"; the editable note covers it after the fact.
- **Online-gating or force-syncing before adjusting a shared account** — breaks offline-first for a narrow staleness window, on a non-destructive action.
