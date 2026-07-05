# "Reset rollover" acts on the target month itself, not the month after (amends ADR-0036 follow-up)

## Context

Beta feedback (V1.6.3 Item 2): "Reset rollover should happen to the primary month target, not before or after."

The rollover carry mechanic (ADR-0036): `BudgetProgressCalculator.effectiveLimit(M)` adds the previous month's leftover/deficit into month M when `M.rolloverEnabled`, recursing backward through consecutive `yearMonth` rows and short-circuiting at any row whose own `rolloverEnabled` is false. The "Reset rollover" action was added as a same-day follow-up (no new persisted marker — `rolloverEnabled=false` *is* the sever lever).

**The bug, confirmed in code:** `BudgetsViewModel.resetRollover(row)` passes the **currently-displayed** month M's budget, but `ResetBudgetRolloverUseCase` computes `nextMonth = M+1` and clears `rolloverEnabled` on **M+1** (creating that row if absent). So a user staring at month M — seeing a weird accumulated carry dragged in from prior months — taps "Reset rollover" and **M still shows the same wrong number**; only M+1 changed. The reset landed *after* the month they targeted. That is exactly "not before or after [the target]."

## Decision

**Change `ResetBudgetRolloverUseCase` to clear `rolloverEnabled` on the passed-in month M itself, instead of creating/modifying M+1.**

1. Flip `budget.rolloverEnabled = false` and upsert **M**. Keep the existing `require(budget.rolloverEnabled)` guard (you can only reset a month that is currently rolling).

2. **Effect on the number the user is looking at:** M stops inheriting M−1's carry immediately → M shows its own `amount`. The reset is now visible on the target month. Because `effectiveLimit` short-circuits at any `rolloverEnabled=false` row, everything **before** M is now unreachable — the chain restarts at M, and M+1 (rollover on by default) inherits only M's *own* `amount − spent`. A clean fresh start *from* M.

3. **Removes** the old surprising side effect where reset silently *created a next-month budget* (defaulting to M's amount).

4. **Accepted consequence:** after reset, **M's rollover toggle now reads OFF**. This is honest — "reset" means "this month no longer carries anything in." Resuming is automatic next month, or the user can knowingly re-toggle M on (which re-inherits M−1).

   *Rejected: keep M's toggle ON but zero only the carry-into-M.* That needs a persisted "carry = 0 at M" marker — a new field/mechanism ADR-0036 deliberately avoided. Not worth it; `rolloverEnabled` stays the single lever.

## Consequences

- The use case shrinks: flip the flag on the row it was handed and upsert it; the `nextMonth` lookup / create-if-absent branch is deleted. `BudgetsViewModel.resetRollover` is unchanged (it already passes M).
- Amends the ADR-0036 same-day "Reset rollover" follow-up only; the core rollover math (`effectiveLimit`, symmetric no-floor) is untouched.
- **Tier-1 tests:** update/replace the existing reset-rollover unit tests — reset on M now makes `effectiveLimit(M) == M.amount`, leaves M−1 untouched, and lets M+1 carry M's own `amount − spent`. This is budget math → must be unit-tested per CLAUDE.md.
- Doc note: the `resetRollover` KDoc/comment ("severs the carry-forward into next month") must be corrected to "resets this month's carried-in balance."

## Suggested build

Sonnet, low effort — a small, well-specified change to one use case plus its unit tests.
