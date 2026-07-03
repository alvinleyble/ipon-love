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

## Grilled implementation decisions (2026-07-03, built as V1.5 slice 9)

The sync/sharing interaction is novel — no prior entity has one partner writing a child row that FK-references the *other* partner's parent. These were locked in a grill before building:

1. **Bidirectional contribution.** On a shared goal *both* partners contribute; each contribution row is owned by its author (`user_id` = contributor). This is what makes the append-only ledger necessary rather than merely convenient.
2. **Partner contributions replicate through a dedicated redacting view.** `partner_goal_contributions` (pull-only) maps into the *same* Room `goal_contributions` table as own rows, exactly like `partner_notes`. `savedAmount` then sums both authors' non-deleted rows via a pure calculator. New `SyncTable.PARTNER_GOAL_CONTRIBUTIONS` + `PARTNER_SAVINGS_GOALS`.
3. **Redaction + purge.** Both partner views null content when the row is deleted or its parent goal is unshared/deleted; un-share/delete **retain `couple_id`** (only unpair nulls it) so the transition still crosses. Purge signals: goal → `!isShared || isDeleted`; contribution → `amount == null` (folded into `isDeleted` in the mapper, since the entity's amount is non-null money). Purging a goal replica also **cascade-purges that goal's partner contribution replicas** as an ordering-window safety net.
4. **RLS across ownership.** `savings_goals` splits into owner-only write policies + a broader **own-or-couple-shared SELECT** (a partner must read a shared goal's *base* row so a `goal_contributions` insert can reference it — a view can't satisfy an RLS sub-select). A shared goal has no per-field privacy, so exposing its base row leaks nothing the view didn't. `goal_contributions` insert is gated through that shared select.
5. **Unpair leaves benign orphans.** Your own contributions to an ex-partner's goal are left (owned by you, not purged). They're invisible (the goal is gone) and the calculator ignores contributions whose goal is absent, so they never leak into a sum. Not soft-deleted — that would erase real history from the partner's now-personal goal.
6. **Delete cascade.** Creator-only, soft, confirmed. Soft-deletes the goal + the creator's *own* contributions; the partner's contribution rows fall out via the goal-deleted redaction (RLS forbids the creator writing them).
7. **LWW-safety.** Every contribution uses a fresh **random `UUID`** (never deterministic, unlike debt-netting), so concurrent adds mint independent ids that both survive the merge. `savedAmount`/`reached` are a pure calculator; a re-pull is idempotent (upsert by id) and never double-counts. Editing/deleting your own contribution is a single-author LWW write — no cross-partner conflict.
8. **FK order.** `SAVINGS_GOALS` before `GOAL_CONTRIBUTIONS` (push is FK-ordered); partner views appended after their owned counterparts. Cursors key off `SyncTable.name`, so placement is safe.
9. **`reached` is a persistent derived visual** (`saved ≥ target && !archived`), not a one-shot event — no dedup store, correct on both devices, handles dip-below/re-cross for free.
10. **Own pinnable module** (`NavRegistry.SAVINGS`, in More by default), list + create/edit + separate detail/ledger screen, archive toggle included.

**Pre-ship:** the two tables, two views, RLS policies, triggers, indexes, grant, and the `unpair()` amendment must be applied to live Supabase (staging) before on-device sync works — schema.sql is updated but not yet run on the project.
