# Reimbursable pass-through: an `exclude_from_analysis` flag on categories, not a Receivables entity

## Context

Alvin asked whether the app covers "expenses reimbursable by my employer" (v1.6.9 Item 43, grilled 2026-07-22). Today it doesn't cleanly: you front a work expense, it shows as **spending** in Analysis; the employer pays you back, it shows as **income** — but it's a pass-through, neither figure is truly yours, so both distort your charts. The obvious framing — a full "Receivables & Payables" module with a counterparty, pending/settled status, and a running total like the Partner Debt Tracker gives couples — is real new scope, and `partner_debts` doesn't generalize to it (`borrower_id`/`lender_id` are both couple-member `users` rows, not an external third party like an employer or friend).

The grill established the actual itch is **un-polluting Analysis**, not building a tracker. There is a precedent for exactly this semantic: the `is_settlement` flag on `transactions` (ADR-0019 #14) means "real money — counts toward balance — but not spending/income, so Analysis excludes it." Reimbursables want the same treatment, lifted to the category level.

## Decision

1. **A single `exclude_from_analysis: Boolean` column on `categories`, default `false`** — not a new entity, no counterparty, no status. It syncs like any other category attribute (ADR-0009 order unchanged); Room bumps **v27 → v28** (AutoMigration, NOT NULL default 0).

2. **Pass-through / static, never status-dependent.** A reimbursable is excluded from Analysis the moment its category is flagged, regardless of whether it has been paid back. The eventual repayment is an ordinary income transaction filed under an excluded income category, so it too is excluded. Balances stay honest throughout (the account really dips and recovers). **Rejected:** an "out-of-pocket" model where the expense counts as spending until reimbursed, then retroactively washes out — that requires per-expense reimbursed/pending status, which drags the rejected tracker back in through the side door.

3. **The flag is available on both expense and income category types.** An expense-only flag would leave the repayment (income) leg re-polluting the income/net side (`DailyNetCalculator` nets income − expense). The editor toggle is labeled neutrally — **"Exclude from spending reports"**, not "Reimbursable" (which reads wrong on an income category) — with helper text naming reimbursements as the headline case. A **"Reimbursable" (expense) + "Reimbursement" (income)** category pair is pre-seeded in the onboarding Templates step with the flag set, so the feature is discoverable without spelunking the editor.

4. **Exclusion boundary (mirrors `is_settlement`):** a flagged category's transactions **show in the Records ledger** and **count toward account balance** (ADR-0007) — they are real money — but are **excluded from Analysis** (donut / expense-flow / calendar-net), **Budgets**, and the **couple Combined view**. Combined shows both partners' non-private transactions, so a reimbursable reaches it unless stopped; it is excluded there too (an employer reimbursement isn't household shared spending). **Rejected:** letting it show in Combined (re-introduces the exact pollution on the one screen couples read together); excluding it from balance or the ledger (it's real money that moved).

5. **Calculators stay pure; exclusion is an upstream filter.** The Analysis/Budgets/Combined calculators are pure functions over `List<Transaction>` and don't know category flags. At the three feature boundaries — which already observe categories — the excluded category-id set is derived and matching transactions are dropped before the calculator runs, via one shared, unit-tested helper. Excluded categories are also hidden from the budget-category picker (you wouldn't budget a pass-through bucket). **Rejected:** denormalizing the flag onto each `Transaction` at map time (a category↔transaction join the mappers don't do today, and a changed flag wouldn't retroactively reclassify history); pushing category-flag awareness into each pure calculator (more invasive than a boundary filter).

6. **Free, unconditional — not in the paywall feature map (ADR-0044).** Marking a category pass-through is basic bookkeeping accuracy, the same class as creating or assigning a category (all free). Gating it would leave a free user's Analysis knowingly wrong — against "recording your own money is never gated."

## Consequences

The new column crosses to the partner via a **one-line add to the `partner_categories` redacting view** (ADR-0004/0005) — without it, Combined cannot see the partner's flag and can't exclude their reimbursables. Budgets gain their **first-ever** transaction-exclusion filter: `BudgetProgressCalculator.spent()` previously honored no flag at all (which is also why partner-debt settlement legs leaked into the overall budget — fixed separately as v1.6.9 Item 45; the two `!isSettlement` / excluded-category filters now sit side by side there). The same design generalizes beyond reimbursements into a clean "don't count this category" escape hatch (investment pass-throughs, misc money that isn't spend/income). This is the same call already made for personal loans (no V1 entity — notes + transactions): the third-party-IOU/Receivables framing was considered and deliberately declined.

## Rejected alternatives (summary)

- **A full Receivables & Payables / IOU module** (counterparty + pending/settled status + running total) — genuine new scope for the un-pollute-Analysis need; same shape as the already-declined personal-loans entity.
- **Out-of-pocket, status-dependent exclusion** — needs per-expense reimbursed status (a mini-tracker).
- **A per-transaction `is_reimbursable` flag** (mirroring `is_settlement`) — a `transactions` migration + an Add/Edit-transaction UI control + every aggregator (incl. `BudgetProgressCalculator`) learning a new field; the per-category flag is lighter and fits how reimbursables cluster, at the cost of a reimbursable expense keeping its "true" category — an acceptable trade for a coherent work-expense bucket.
- **Expense-only flag** — lets the reimbursement income leg re-pollute net/flow.
- **Gating it Premium** — leaves free users with knowingly-wrong Analysis.
- **Conflating with `is_private`** — "hidden from partner" and "not real spending" are different concepts.
