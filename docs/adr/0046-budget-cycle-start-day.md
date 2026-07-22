# Budget cycle start day: local display-class preference, personal budgets only, label reinterpretation

> **Superseded/Reversed (v1.7.0 Item 4, 2026-07-23):** the payday-aligned cycle only ever re-windowed the Budgets tab — Records/Combined/Analysis/paywall stayed calendar-month by design (§1 below), so the "matches how you actually experience cash flow" payoff broke the moment you checked Analysis. Not worth the added settings surface + edge-case handling (short-month clamping, personal-vs-shared-calendar split, ADR-0047's amendment below) for a benefit that only landed in one of four places. Reverted to calendar-month-only budgets; this ADR is kept for the rationale/history, not as current behavior. See `docs/build/v1.7.0.md` Item 4.

## Context

V1.6.5 Item 10b, grilled 2026-07-13. Users paid mid-month (PH kinsenas/katapusan — the 15th and 30th) experience "this month's money" as payday-to-payday, not calendar months. A calendar-month budget puts payday mid-budget, so every budget month reads "broke half, flush half." The reference app (MyMoney) ships this as "Start day of month."

Everything in the budget machinery keys off `Budget.yearMonth`, a synced `"2026-07"`-style label: spent-bucketing (`yearMonthKey(txn.date) == budget.yearMonth`), the rollover chain (`minusMonths(1)` on the label, ADR-0036), the per-month cap count (paywall S7), alerts (`budget.yearMonth != currentMonth`), and duplicate-to-next-month. Separately, the app-wide "month" (Records/Combined/Analysis windows, ADR-0030/0032) has become paywall-load-bearing since S10 (`MonthWindow.canStepForward`/`canStepBack`, `AnalysisPeriodRange.canStepBack` enforce the forward cap + the −12mo `DEEP_HISTORY` wall at calendar-`YearMonth` granularity).

## Decision

**A "Budget month starts on day N" setting (default 1), applying to personal budgets only, stored locally, implemented by reinterpreting the existing `yearMonth` label's date range — no schema change.**

1. **Scope: budgets only.** Records, Combined, Analysis, and the paywall stepper predicates stay on true calendar months, untouched. Recurring is unaffected by construction (`RecurringScheduler` steps each rule from its own `nextDate`, never a month boundary — the v1.6.5 booking's "recurring date math" worry was wrong). Standard finance-app split: budget period ≠ transaction-history view. Also sidesteps any couple ambiguity in the shared Records/Combined windows.

2. **Personal budgets only; the shared couple budget stays calendar-month.** A per-user cycle can't bind a two-user row: each partner's device independently computes the shared budget's "spent," so differing start days would show two different totals for the same row. The Budgets tab observes personal rows only (`coupleId IS NULL`) and the shared budget renders solely in the Couple view, so no screen ever mixes the two windows. Upgradeable to a couple-level setting later without undoing anything. (`CombinedViewModel` computes shared spent from the combined ledger's calendar `monthKey` and never calls `BudgetProgressCalculator.spent` — the shared path stays calendar by construction; pin with a test, no guard needed.)

   > **Amendment (v1.6.6 Item 35 / ADR-0047, 2026-07-17):** shared budgets now *also* render in the Budgets tab (no longer "solely in the Couple view") — the Budgets tab merges personal + shared rows. The calendar-month rule for shared budgets is **preserved and made explicit**: `BudgetRowsCalculator` applies the payday `startDay` to personal rows but a fixed calendar `startDay = 1` to shared rows (`CALENDAR_START_DAY`), so a shared budget's spend never depends on either partner's personal start day. The upgrade path to a couple-level start day is unchanged.

3. **Local DataStore, not a synced `users` column.** The rule the codebase follows: sync a pref only if the partner or another device needs it to render correctly (`accent_color` yes; theme/privacy/currency no). The partner never sees personal budgets. Reinstall loses the pref → budgets read as calendar months until re-set — self-healing, nothing lost (spent is derived). If it graduates to couple-level, *that's* when it earns a synced column.

4. **Keep `yearMonth` as an opaque cycle key; reinterpret only its window.** A budget row keeps `"2026-07"`; one pure function maps label + start day → date range. Label = the cycle **starting** in that month (`"2026-07"` @ start-day 15 = Jul 15 → Aug 14). Boundaries computed **statelessly per label** — `K.atDay(min(startDay, K.lengthOfMonth()))` — never by iterated `plusMonths` (which drifts: Jan 31 → Feb 28 → Mar 28). Consecutive labels stay consecutive `YearMonth`s, so rollover/cap/duplicate/alerts keep working untouched. `startDay = 1` is byte-identical to today.

   *Rejected: explicit start/end columns on the budget row.* Duplicates derivable data, costs a migration + Room bump + sync threading, and turns every future setting change into a mass row rewrite (+ push churn). House style is derive-don't-store (ADR-0007 balances, ADR-0036 read-time rollover).

5. **Allowed days 1–31 with short-month clamping** (start day 30 → the Feb cycle starts Feb 28). Not capped at 28: the 30th is *the* PH payday. Clamped boundaries are monotonic, so windows stay contiguous — no gaps or overlaps; 31 effectively means "last day of month."

6. **Setting changes are retroactive and stateless: the new day reinterprets every budget month, past ones included.** Forward-only versioning was rejected — it needs a persisted change history (lost with the DataStore pref anyway, making it pointless) and creates an orphaned seam at the changeover (days belonging to two cycles or none). Deferred-apply just delays the same choice. Retroactive is what MyMoney does, is explainable in one sentence, and is free + fully reversible: spent and rollover are derived at read time, so re-windowing writes nothing.

   Accepted consequences: history re-renders under the new definition (mitigated by the settings caption "Applies to all budget months, including past ones"), and on the day of a change a budget re-crossing its alert threshold may re-fire one duplicate alert (`BudgetAlertStore` dedup keys by month — one-time, harmless).

7. **UI: a row in PersonalizeScreen under a new "Finance" section** (Item 10a's default-account pref lands beside it later; the v1.6.5 booking's "don't fold 10b into 10a" holds — separate slices), opening a dialog with a 1–31 day picker, "1st (default)" preselected. When start day ≠ 1, the Budgets header shows the cycle's date range ("Jul 15 – Aug 14") instead of a bare month name — the label's month-name no longer matches the wall clock (on Jul 13 @ start-day 15, the current cycle is keyed `"2026-06"`).

## Consequences

- **Cycle-aware call sites (the full set, verified):** `BudgetProgressCalculator` (+`yearMonthKey` → cycle-key variant with `startDay`, default 1), `BudgetsViewModel` (current-cycle detection + header range), `CheckBudgetAlertsUseCase` + `BudgetAlertWorker` ("current month" → current cycle key; worker reads the pref), `ResetBudgetRolloverUseCase`. `AnalysisCalculator` references `BudgetProgressCalculator` in KDoc only — no live consumer; Analysis stays calendar.
- **New:** `core`-adjacent pure cycle-key/window functions; `finance_prefs` DataStore repo + observe/set use cases (privacy-mode pattern); Personalize "Finance" section + picker dialog.
- **Untouched:** schema, Room (stays v25), sync, `MonthWindow`, `AnalysisPeriodRange`, paywall predicates/gates, recurring, shared couple budget, Records/Combined/Analysis.
- **Tier-1 tests (budget math → mandatory):** cycle-key at start-day 1 ≡ `yearMonthKey`; day-15 boundary bucketing (first/last instant of the window); day-31/30 clamp contiguity across February (no gap/overlap); spent-bucketing under a non-1 start day; rollover chain under a non-1 start day; alert current-cycle selection; shared-budget-stays-calendar pin.

## Suggested build

Sonnet, medium effort — the grill resolved every design call; what remains is contained pure date math + pattern-following DataStore/UI wiring. (The Opus flag on the original booking was for this design work, now done.)
