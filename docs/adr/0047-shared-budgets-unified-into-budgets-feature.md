# Shared budgets unified into the Budgets feature: creation-time scope selector, immutable scope, no `created_by`

## Context

v1.6.6 Item 35, grilled 2026-07-17. The couple's "shared budget" shipped in V1 as a single overall-only card on the Combined view — one joint number, no categories, no rollover, manually re-set every month — while personal budgets grew per-category limits and rollover (ADR-0036) on the same `budgets` table. `budgets.couple_id` has existed since V1 (personal: `user_id` set / `couple_id` null; shared: the reverse, with a `budget_owner_chk` constraint and the `budgets_couple` RLS policy) but was never used for anything richer than that one card. The instinct to *remove* the shared budget was really a reaction to the weak implementation, not the concept.

The other couple-shareable entities — accounts, categories, savings goals — use the **ADR-0018 share toggle**: a row is created personal, then a "Share with partner" / (creator-only) "Make personal" menu action flips it couple-owned, backed by a `created_by` column that records the creator so unshare/unpair can revert-to-creator and so a non-creator can't wedge the push (v1.6.5 Item 20). Budgets have **no `created_by` column**.

## Decision

**Fold shared budgeting into the real Budgets feature (Manage → Budgets) as first-class couple-owned `Budget` rows with categories + rollover — but designate scope with a creation-time selector, not the ADR-0018 share toggle. Scope is immutable after creation. No `created_by`, no schema/Supabase migration, Room stays v26.**

1. **Creation-time Personal/Shared selector, deliberately NOT the share toggle.** The budget editor gains a Personal/Shared choice, shown only when paired, chosen at creation; a budget's scope is fixed once it exists (edit amount/category/rollover, not scope — to change scope, delete + recreate, or duplicate-to-next-month and pick the other scope). This diverges from accounts/categories/goals because **budgets are per-month rows** (`yearMonth`): a post-hoc toggle would force re-sharing every month and could strand a rollover chain across mixed scopes, and the toggle's revert-to-creator needs a `created_by` budgets don't have. A shared budget is *born joint* (via `upsertSharedBudget`, as the old card already did), so there is no personal→shared row flip and no wedge risk — the ADR-0018 machinery is unnecessary here.

2. **Unpair keeps ADR-0008 semantics unchanged.** Shared budgets are still *soft-deleted* on unpair ("a jointly-set number neither person should inherit alone") — not reverted to a creator. Because there's no share/unshare transition and no `created_by`, this ADR touches none of ADR-0008.

3. **Shared budget spend = both partners' combined non-private transactions**, per category, computed from the unbounded combined ledger (`observeCombinedTransactionsUnbounded`); personal budgets keep counting own transactions. A `Private` transaction never counts toward a shared budget (consistent with ADR-0011). Rollover works via the same per-row toggle (ADR-0036, unchanged); the chain stays within one scope (shared chains with shared) — kept consistent by #1's immutable scope + duplicate-forward carrying the flag.

4. **Shared budgets use calendar months, not the personal payday start day.** A jointly-owned budget can't follow one partner's personal "budget month starts on" day (which differs per device), so shared rows are always calendar-month — preserving the pre-Item-35 overall-shared-budget behaviour and ADR-0046's rationale. Personal rows still honour the payday start day.

5. **New `maxSharedBudgets` cap (Scope.SHARED, per-month), gated at shared-budget creation.** Mirrors the accounts/categories/goals personal+shared cap pairs; `maxBudgets` is renamed `maxPersonalBudgets` for symmetry. Closes the prior gap where shared budgets were silently uncapped. Dormant until enforcement flips, like every other cap.

6. **The Combined view keeps a read-only shared-budget summary.** It lists the couple's shared budgets' progress (self-hiding when there are none) and points to the Budgets tab; all creation/editing moves to the Budgets tab. The couple screen keeps budget *visibility* (ADR-0011) but not editing.

## Consequences

- **No migration.** `couple_id`, `budgets_couple` RLS, `budget_owner_chk`, `idx_budgets_couple`, and the `unpair()` soft-delete step all stay (the opposite of the earlier "remove it" plan). Room stays **v26**. The one live staging shared-budget row is already a valid overall shared budget under this model.
- The `Budget` domain model gains an `isShared` flag (projected from `couple_id != null`); `BudgetRepositoryImpl.upsertBudget` is made ownership-preserving so editing an existing shared row never re-stamps it personal.
- Budgets are now the one couple-shareable entity **without** an in-place share toggle. That's a deliberate, documented asymmetry justified by their per-month nature — not an oversight. If a "convert this budget's scope" need ever emerges, it would require adding `created_by` (a future migration), consciously deferred.
- **Rejected:** the ADR-0018 share toggle for budgets (rejected for the per-month re-share friction + `created_by`/ADR-0008 cost); removing shared budgeting entirely (rejected — the concept is good, only the old implementation was weak); a couple-level "budget month start day" for shared budgets (deferred — calendar months are the safe default, ADR-0046 point 2 upgrade path preserved).
