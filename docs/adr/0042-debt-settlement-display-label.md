# Debt settlement legs get a display-only "Debt settlement" label keyed off `is_settlement`, not a real category

## Context

Beta feedback (V1.6.3 Item 7): "User paying a debt, uncategorized. User adding to account a debt payment, uncategorized." Debt settlement transactions show as "Uncategorized" in Records, which reads as broken.

This is **not a data bug — it's by design.** `SettleDebtUseCase` (payor EXPENSE leg) and `AddSettlementIncomeUseCase` (receiver INCOME leg) both create their transaction with `categoryId = null` and `isSettlement = true`. The `is_settlement` flag already carries the exact semantics: it *counts toward account balance but is excluded from Analysis* (ADR-0019 #14). The problem is purely how Records **labels** a category-less row.

There is already a precedent for labeling category-less rows by type/flag rather than category: `TransactionsViewModel` renders a `TRANSFER` row as `title = "Transfer"`, and everything else falls to `categoryNames[categoryId] ?: "Uncategorized"`. A settlement leg (EXPENSE/INCOME, `categoryId = null`) hits that fallback → "Uncategorized." (Transfers are `categoryId = null` too — schema comment "null for transfers".)

## Decision

**Add a display-only branch in `TransactionsViewModel` that renders any `isSettlement` row with a fixed "Debt settlement" label, mirroring the existing "Transfer" branch. No real category, no schema change.**

1. **Reuse the existing `isSettlement` flag** — it already means "this is a debt leg, not spending." Don't add a field ([[feedback-prefer-existing-mechanism-over-new-field]]).

2. **One label for both legs — "Debt settlement".** The expense/income direction and the account already tell the user whether they paid or were repaid; two labels ("Debt payment" / "Debt repaid") add nothing.

3. **Do NOT create a real "Debt" category.** A real category would be selectable for normal expenses, pollute the category picker and budgets, and — worst — re-enter Analysis unless specially excluded, undoing ADR-0019 #14's exclusion. The synthetic label keeps settlements out of category space entirely.

4. **Scope is `isSettlement` legs only.** The "paid for partner" *original* expense is a real, user-categorized purchase (the toggle only *additionally* spawns the debt); it already has a category and is untouched.

## Consequences

- A few lines in `TransactionsViewModel` (a branch above the `?: "Uncategorized"` fallback); optionally a small icon to match "Transfer". No schema, DAO, sync, or Analysis change.
- Analysis behavior is unchanged — settlements were and remain excluded (ADR-0019 #14).
- **Tier-1 test:** a `TransactionsViewModel`/mapper test asserting an `isSettlement` row titles as "Debt settlement" and a normal category-less row still reads "Uncategorized". (VM display logic — light, but cheap to lock.)

## Suggested build

Sonnet, low effort — a single display branch mirroring an existing one.
