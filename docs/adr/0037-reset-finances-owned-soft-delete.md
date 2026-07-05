# Reset finances is an owned-row soft-delete, not a structure wipe

## Context

Users want a "restart fresh" action to clear their money data without deleting their account. The naive reading ("delete transactions and accounts") collides with three existing rules: deletes are soft and sync (ADR-0010/0002), balances are derived from `opening_balance` + ledger (ADR-0007), and partner/shared rows must never be written back (ADR-0004/0005/0011). We needed to decide exactly what dies, what survives, and how it reaches the server.

## Decision

**Reset finances** is a Settings action, gated behind password re-authentication (reusing the `ForgotPinDialog` re-auth pattern) with a live-count consequence summary. It soft-deletes only the rows the user **owns** (`owner/userId == me`) across four tables — transactions, recurring rules, budgets, and goal contributions — in a **single local Room transaction**, stamping `updated_at` + `pending_sync` on each, then fires an immediate offline-tolerant interactive push (ADR-0012). A `ResetFinancesUseCase` orchestrates it via a generic bulk-soft-delete helper (extracted so the future "Delete my account" path can reuse it).

**Survives the reset:** accounts, categories, savings-goal *definitions*, opening balances, all notes, all couple/shared/partner state (couple budget, shared goals, partner debts, partner-replicated rows), the `users` row, auth session, and pairing. Account balances simply fall to their opening balance because the ledger is empty.

## Consequences

- **Symmetric with the sync model, not a local hack.** A local-only wipe was rejected: the next pull (`server_rev > cursor`) would resurrect every "deleted" row. Soft-delete-and-push is the only design consistent with ADR-0010.
- **Deliberately not "Delete my account"** (Post-V1 Horizon #11). That removes the identity — users row, auth, couple membership. Reset empties the ledger; delete removes the person. They share only the bulk-soft-delete helper.
- **Ownership filter is the couple safety boundary.** Filtering every soft-delete by `owner/userId == me` prevents the reset from becoming a backdoor that wipes partner-replicated or jointly-owned shared rows. Shared transactions the user *authored* are their own rows and are wiped (the partner losing visibility is the correct consequence); the couple budget, shared goals, and partner debts are out of scope.
- **Recurring is safe against zombie regeneration.** `MaterializeRecurringRulesUseCase`/`activeRules()` filter `isDeleted = 0`, and materialize is insert-if-absent by `DeterministicUuid.v5("ruleId:date")`, so soft-deleted rules stop generating and tombstoned occurrences are never resurrected.
- **`opening_balance` is untouched** — it is account *setup*, not money movement, so a reset lands you at your opening balance rather than ₱0. Resetting it to zero was considered and rejected as out of Tier-1 scope.
