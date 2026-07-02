# Shared savings goals with a derived contribution ledger

A `SavingsGoal` is personal-by-default, optionally shared to the couple via the generic sharing layer (not hard-coded to goals, per the CLAUDE.md scalability principle). Its `savedAmount` is **not** a stored field — it is derived from append-only `GoalContribution` rows, each owned by its contributor.

A shared mutable counter would be silently corrupted by row-level LWW (ADR-0001/0003): two partners contributing concurrently both write `savedAmount`, and the later `updated_at` clobbers the other's contribution. The append-only ledger sidesteps this (independent row ids never conflict), reuses the same derived pattern as account balance (ADR-0007), and yields a contribution history for free — which is motivating in a couples app.

## Decisions

- **Goal metadata (name, target, date) is creator-owned** — avoids LWW clobber on the target. Contributions are always owned by their contributor (only they edit/delete their own).
- **Delete is creator-only, soft (ADR-0010), and cascades** to the goal's contributions, behind a confirmation.
- **`reached` is derived** (`savedAmount ≥ targetAmount`), not stored; reached goals stay listed with an in-app celebration until archived. No push notification (V1 scope = budget alerts only). Manual `isArchived` soft flag.
- **Icon + color only, no photo upload** (V1 allows image uploads for note attachments only).
- **Goals is its own pinnable module** (More by default), not a 4th Manage tab — Manage is configuration (accounts/categories/budgets); a shared, social, motivational feature belongs with Notes/Couple, not in a config drawer.

## Considered Options

- **Manual stored `savedAmount`**: fine for personal-only goals, but data-lossy the moment a goal is shared. Rejected once shared goals were chosen for V1.
- **Contribution moves real money (envelope model)**: deferred. Auto-spawning an expense per contribution would double-count against budgets/analysis. True earmarking against account balances ("savings envelopes") is a deliberate post-V1 feature with its own design pass, because it restructures how accounts and balances work app-wide (ADR-0007). V1 contributions are standalone bookkeeping amounts that do not touch balances.
- **Sync:** new `savings_goals` + `goal_contributions` tables, redacting partner views (ADR-0005), inserted into the ADR-0009 FK order after `budgets`; Room version bump, DTOs with `BigDecimalSerializer`.
