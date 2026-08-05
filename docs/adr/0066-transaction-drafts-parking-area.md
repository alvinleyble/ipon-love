# Transaction drafts live in their own table, sync as rows but not photos, and are never partner-visible

**Status:** accepted (2026-08-05, grilled) — [v1.7.3 Item 8](../build/v1.7.3.md#item-8--transaction-drafts-a-parking-area-inside-records-third-exit-from-the-new-transaction-form). Amends [ADR-0062](0062-receipt-scan-ocr-prefilled-draft.md) decision 7 by consequence (see below). Grill report: `data/item8-drafts-grill/report.md`.

## Context

Booked 2026-08-04 off Alvin's request for a parking area inside Records: *"a user is usually busy and has only time to sit down and fix things a couple minutes a day or in a week, it's good to have a parking area for these transactions to settle their Love Ipon tracking — so a draft would help them see what's on their queue once they sit down."* The immediate trigger is [ADR-0062](0062-receipt-scan-ocr-prefilled-draft.md)'s scan flow: a photo prefills the New transaction form, and today the user's only exits are finish it or lose it. Drafts add a third — `Save as draft` — alongside `Cancel` and `Save`. Alvin's constraint on the split: *"Drafting a transaction is free, but receipt isn't, so there should be no friction there."* He accepted a schema change up front: *"then let it have schema change as it is. we need the drafts."*

Five findings shaped the design before any decision was made.

1. **A draft cannot satisfy the `transactions` row shape.** `type`, `amount` and `account_id` are all `not null` (`supabase/schema.sql:166-169`), and `TransactionValidator.validate` (`TransactionValidator.kt:36-58`) additionally requires `amount > 0`, a non-blank account, and a category for INCOME/EXPENSE. A draft may legitimately fail all four — ADR-0062 *infers* Category and Account and infers nothing at all on a fresh account (that ADR's own "weakest on a fresh account" consequence), and forcing an account choice before parking is exactly the friction Alvin ruled out.
2. **The house has already priced a status flag on `transactions`.** [ADR-0048](0048-confirm-on-arrival-recurring.md) decision 2 rejected a `status = pending` column *"which would have forced `WHERE status = confirmed` into every balance/budget/Analysis query — wide surface area, easy to miss a call site, easy to get wrong."* That decision could avoid storage entirely (a pending occurrence is derivable from rule + cursor + date math); a draft is genuinely user-authored and must be stored somewhere, so the question here is *which table*, not *whether*.
3. **The exclusion surface is 19 client sites plus a frozen server view.** Enumerated in full in the grill report §3.2: twelve `TransactionDao` queries, five domain calculators, export, and the budget-alert worker — plus `partner_transactions` (`supabase/schema.sql:651-670`), whose purge predicate is frozen at [cross-platform-contract.md §5.3](../web/cross-platform-contract.md#53-partner-data). Two of the twelve are the dangerous ones: `observeHasAnyTransaction` (`TransactionDao.kt:47`), which gates first-run onboarding seeding on emptiness (§6.2 of the contract), and `observeCombined` (`:58-65`), which carries **no owner filter by design** — so a partner's draft would land straight in the combined feed.
4. **The editor already pre-generates the future transaction's id.** `TransactionEditorState.id` is *"always pre-generated so receipt image files can be keyed to this transaction before save"* (`TransactionEditorState.kt:15`). This is load-bearing for decision 5.
5. **A draft's receipt photo is on a collision course with two sweeps.** `CompressReceiptUseCase` writes `filesDir/receipts/{imageId}.jpg` at pick time (`:29`) while the `transaction_images` row is deferred to save, and `CleanupOrphanedReceiptsUseCase` deletes every file in that directory with no matching row (`:20-26`, predicate at `:32`). ADR-0062 decision 9 adds a second, age-based sweep over `cacheDir/scans`.

## Decision

### 1. Drafts live in their own table, `transaction_drafts` — not behind a flag on `transactions`

```sql
create table transaction_drafts (
    id            uuid primary key,                                  -- == the future transactions.id
    user_id       uuid not null references users(id) on delete cascade,
    type          transaction_type,
    amount        numeric(14,2),
    category_id   uuid,        -- no FK, deliberately (see below)
    account_id    uuid,
    to_account_id uuid,
    note          text,
    date          timestamptz,
    is_private    boolean not null default false,
    receipt_count int not null default 0,                            -- decision 4
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    is_deleted    boolean not null default false,
    server_rev    bigint
);
```

**Every content column is nullable, because a draft is a partial form** (finding 1). This is the whole reason the table exists: the flag option would have forced either dropping `not null` from `type`/`amount`/`account_id` on the app's core money table — permanently, for every real row, so that it could store rows that are not money — or writing placeholder values (`amount = 0`, an arbitrary account), which is how a leaked draft stops reading as obviously wrong and starts reading as a plausible ₱0 transaction against the wrong account. ADR-0062 already flags that asymmetry: *"a wrong inferred `Account` corrupts a balance, where a wrong `Category` only misfiles a chart."*

**No FK on `category_id` / `account_id`**, matching the same pull-order tolerance `transfer_fee_transaction_id` is given (`supabase/schema.sql:177`): a parked draft must survive its category being archived or deleted while it waits, and must not fail to land because a pulled batch arrived out of dependency order. It degrades to "Uncategorized" on read, exactly as a historical transaction does.

**Room v31 → v32.** The Room entity additionally carries `localImageIds`, which is **local-only** — never in the DTO, never pushed — the same treatment `pendingSync` gets (contract §2.2) and `TransactionImageEntity.localPath` gets today. Decision 6 is its only consumer.

**Not persisted in a draft:** `paidForPartner`, `amountOwedText`, `transferFeeText`. Each spawns linked rows in another feature ([ADR-0019](0019-transaction-linked-partner-debt.md), [ADR-0031](0031-transfer-fee-as-cascading-linked-expense.md)) and none is meaningful until the transaction is real; a draft round-trips them blank.

### 2. Money-math exclusion is by construction — there is no exclusion predicate

**No query in finding 3's list changes. Not one.** A draft cannot count as money because it is not in the ledger table. This is the decision's entire point, and it is why the separate table wins despite costing more code: the flag option's ~19 `WHERE is_draft = 0` clauses have no compile-time membership check, so a DAO query added by a future agent joins the set silently, and the failure is a parked draft counting as money spent.

Specifically preserved without edit: derived balance ([ADR-0007](0007-derived-account-balance.md)) and everything downstream of it (`AccountBalanceCalculator`, the Accounts tab, `ObserveNetAssetsUseCase`, the Glance balance widget); `AnalysisCalculator`, `DailyNetCalculator`, `ExpenseFlowCalculator`; `BudgetProgressCalculator`; `CombinedLedgerCalculator`; export ([v1.7.0 Item 6](../build/v1.7.0.md#item-6--general-export-facility-csv--pdf--zip-receipt-photos-included)); `BudgetAlertWorker`; the onboarding-emptiness guard; and the recurring dedup set.

### 3. Drafts are own-user-only. A partner can never see one — structurally, not by policy

RLS is `for all using (user_id = auth.uid())` — the `notifications` shape ([ADR-0053](0053-notification-inbox-synced-source-of-truth.md) decision 2: *"own-user-only, no partner variant, never replicated"*). **There is no `partner_transaction_drafts` view and none may be added.**

The product argument agrees with the structural one: [ADR-0011](0011-combined-view-scope-no-partner-balances.md) scopes the combined view to *shared spending*, and a draft is not spending — it is a half-typed form that may carry a wrong amount, or a receipt for the partner's own gift.

**Consequence: [ADR-0004](0004-partner-data-replicated-rls-visibility-gate.md), [ADR-0005](0005-redacting-partner-views-for-convergence.md) and [ADR-0011](0011-combined-view-scope-no-partner-balances.md) need no amendment, and contract §5.3's frozen purge-predicate table gains no row.** This is the single largest saving from decision 1.

### 4. The draft *row* syncs; the draft's *photo* does not cross until promotion

The row is small text and numbers, and the queue has to survive a lost phone and be visible where the user actually sits down — which is increasingly a laptop. The web client is the near-term cross-platform target and is **online-only with no local store** ([ADR-0063](0063-web-v1-is-online-only.md)), so a local-only queue would be invisible in exactly Alvin's headline scenario.

The photo stays a local file until promotion puts it on the existing `transaction_images` → Storage path. On a second device a receipt-bearing draft renders from the synced `receipt_count`: **"📷 1 receipt — on your other device."**

This gap is **accepted knowingly**, not overlooked — it is the one place this design is strictly worse than the rejected flag option, which would have synced draft photos for free (see Rejected alternatives). Three things make it acceptable: the scan flow is single-device by nature (you photographed it on the phone in your hand); the hand-typed draft — the free-tier majority case and the stronger product pitch — has no photo and syncs perfectly; and the alternative is a second synced image child table plus Storage egress for photos with a high abandon rate, which is poor economics for a ₱249 one-time app.

**Identity is an ordinary random v4 UUID.** Contract §1.5 is explicit that ordinary transactions are v4 and warns *"do not 'helpfully' extend determinism to these."* Two devices drafting two different receipts must produce two rows. **No §1 amendment.**

Sync mechanics are otherwise entirely standard: monotonic offset-corrected `updated_at` ([ADR-0001](0001-client-set-updated-at-with-clock-offset.md)), `pending_sync` outbox ([ADR-0002](0002-sync-cursor-model.md)), row-level LWW ([ADR-0003](0003-row-level-lww-with-note-conflict-copy.md) — two devices editing one draft, last writer wins; acceptable, it is an unfinished form and not a ledger row), tombstone-only ([ADR-0010](0010-keep-tombstones-forever.md)).

### 5. Promotion needs **ordering**, not atomicity — so contract §9 does not fire and no RPC is built

Promoting a draft writes `transactions` (insert), `transaction_drafts` (soft-delete) and `transaction_images` (N inserts). That reads as an all-or-nothing multi-table write, which would engage [contract §9](../web/cross-platform-contract.md#9-atomic-multi-row-writes--android-has-a-primitive-web-doesnt)'s **frozen** forward rule — *"any future feature requiring an all-or-nothing write across multiple tables must be designed as a server-side `SECURITY DEFINER` RPC from the start if web needs to author it"* — and web does need to author it.

**It does not fire, because the draft's id *is* the future transaction's id** (finding 4):

- **Write the transaction first, retire the draft second.** If the retire fails, a stale row sits in the queue; the user re-settles it, which is an **idempotent upsert of the same id**. Money can never double. Worst case is one extra tap.
- The reverse order would lose data outright (draft gone, transaction never written), so **the ordering is a rule, not a preference, and binds every client.**

Promotion is therefore two ordinary REST calls on web and needs no RPC and no `LocalTransactionRunner`. **No §9 amendment.** Android may wrap it in `LocalTransactionRunner.run { }` for tidiness but **must not depend on it**, or the web path silently diverges from the Android one.

### 6. The `filesDir/receipts` sweep learns about drafts through its caller, not its predicate

One line in `CleanupOrphanedReceiptsUseCase.invoke()`:

```kotlin
val knownIds = (repository.allImageIds() + draftRepository.allLocalImageIds()).toHashSet()
```

The pure predicate at `CleanupOrphanedReceiptsUseCase.kt:32` and its existing test are **untouched** — its documented contract (*"a file id with no matching row at all … is unreachable by every other cleanup path"*) still holds exactly; a draft-referenced file simply now *has* a matching record. `allLocalImageIds()` reads the local-only column from decision 1.

Two rules follow:

- **Deleting a draft deletes its receipt files** (or at minimum drops them from `localImageIds` so the next sweep collects them). Otherwise the [v1.7.0 Item 14](../build/v1.7.0.md#item-14--orphaned-receipt-files-are-never-cleaned-up-device-storage-leak) leak returns through a new door.
- **A draft synced from another device carries an empty `localImageIds`** and contributes nothing to `knownIds` — correct, since its files are not on this device either.

Rejected: a separate `filesDir/draft-receipts/` directory with a move on promotion (introduces a file-move failure mode — move fails, row points at nothing — and duplicates the naming convention); and creating the `transaction_images` row at draft time (impossible, `transaction_images.transaction_id` is `not null references transactions(id)`, `supabase/schema.sql:295` — and undesirable anyway, since the uploader would push a photo for a transaction that may never exist).

### 7. `Save as draft` writes the gallery copy — amending [ADR-0062](0062-receipt-scan-ocr-prefilled-draft.md) decision 7

ADR-0062 decision 7 pins the `Pictures/Love, Ipon` copy to *"on Save, never at capture"* and never contemplated a third exit. **Draft-save counts as a commit for that decision's purposes.** Its rejected case was *accidental* pollution — *"abandoned scans the moment a user backs out of an unsaved draft"* — and parking a draft is the opposite: a deliberate act of keeping.

This also **collapses half the cleanup problem**: ADR-0062 decision 9 has `AddTransactionViewModel` delete the full-res `cacheDir/scans` temp immediately after recognise + compress, *"unless the gallery-copy toggle is ON (then decision 7's Save-time write holds it)"*. Because draft-save performs that write, no draft ever holds a `cacheDir/scans` file past its own save — so **decision 9's one-hour sweep needs no draft awareness at all.**

The alternative (draft-save does not write the copy) was rejected because it releases the full-res original at draft time, so the user never gets a gallery copy of that receipt *even after promoting it*, and decision 7 explicitly refuses to substitute the downgraded 1080px re-encode.

### 8. Drafts are free and uncapped, with no paywall surface on the drafts screen

Alvin's ruling, directly: *"Drafting a transaction is free, but receipt isn't, so there should be no friction there."*

- **No new `Feature` enum entry.** The scan gate ([ADR-0062](0062-receipt-scan-ocr-prefilled-draft.md) decision 6, reversed to fully-paywalled) already stops a free user *at the scan CTA*, before a receipt-bearing draft can exist. A second gate downstream is redundant.
- **No `PlanLimits` field.** Every other entity is capped, but a cap on drafts blocks the "just park it" flow that *is* the feature, and CLAUDE.md is explicit that recording your own money is never gated. Pileup is handled by decision 10, not a wall.
- **[subscription-paywall-design.md](../build/subscription-paywall-design.md) §10.1's locked contract is unchanged** — only a documentation line in §8.1's map, so this is not re-litigated later.

### 9. One drafts list, not two tabs

Alvin asked for two tabs (*"Draft and w/ Receipts, and inside the w/ Receipts is where the user can see their drafts with receipts and settle it there"*). **Overturned at the grill, with his approval (2026-08-05).**

Settling happens by tapping a draft back into the New transaction form **either way** — the second tab buys a *filter*, not a different action, and a queue the user is meant to empty should not be split in two. A **receipt thumbnail on the row** shows which drafts have photos at a glance. Since scanning is fully paywalled, the "w/ Receipts" tab would additionally have been premium-only in practice, requiring an upsell empty state for every free user on a screen whose entire purpose is removing friction — and it would have collided with the freeze policy (*"over-cap data goes read-only, never deleted"*), since hiding the tab from a lapsed premium user would hide their existing receipt-drafts.

**Deferred, not rejected:** a "Has receipt" filter chip reusing the [v1.7.0 Item 7](../build/v1.7.0.md#item-7--records-page-filter-feature) multi-select pattern (`TransactionsViewModel.kt:173-178`). Build it only if real usage produces long draft lists — that is the explicit revisit trigger.

### 10. The anti-graveyard mechanism is a pinned Records card, not a notification

- **A pinned "Drafts (N)" card on Records**, opening the drafts screen — a direct clone of `PendingConfirmationsCard` (`TransactionsScreen.kt:213-216`: pinned above the month-scoped list, self-hides when empty). It is both the entry point and the reminder, so it costs nothing extra. Nested nav per [ADR-0033](0033-uniform-nested-nav-graphs-per-module.md).
- **An age label per row** — "parked 12 days ago" — reusing the shape of `comingUpDueLabel` (`ComingUpDueLabel.kt:17`). Mild pressure, no notification.
- **No notification.** [ADR-0048](0048-confirm-on-arrival-recurring.md) decision 6 chose exactly this passive-card shape for pending recurring occurrences and decision 10 deliberately deferred the push to its own item; drafts inherit that ruling rather than reopen it. A notification would also need a new deterministic id format, and contract §1b is **frozen** — a contract amendment for a nag.
- **⚠️ No auto-deletion, ever.** A draft is user data, and the user's mental model is that the app is holding it *for* them; silently expiring one is the worst failure a parking area can have. Tombstone-only ([ADR-0010](0010-keep-tombstones-forever.md)) applies — only an explicit user delete retires a draft.

### 11. `Save as draft` is offered on new transactions only

Hidden when `editor.isEditing` is true. Drafting an edit would leave a shadow copy of a row that already counts in every balance and total — confusing, and it buys nothing.

## Consequences

- **Room v31 → v32**, one new entity, one DAO, one migration. New Supabase table + own-row RLS policy + migration.
- **[cross-platform-contract.md §3.1](../web/cross-platform-contract.md#31-the-single-authoritative-full-ordering) is amended** — a 24th `SyncTable` entry, `TRANSACTION_DRAFTS`, **appended after `NOTIFICATIONS`** so ordinals 1–23 are unchanged (a pure append is the cheapest possible amendment to a frozen list). It earns the same last-place argument `NOTIFICATIONS` has: nothing depends on it, and it must never delay a financial row's push.
- **§7.6 gains one clarifying sentence** — draft-referenced files are excluded from the `filesDir/receipts` sweep. Still an Android-local concern with no web equivalent, exactly as that section already says of the sweep itself.
- **§1, §1b, §5.3 and §9 are explicitly unchanged, having been checked rather than overlooked** — v4 ids (decision 4), no new notification id format (decision 10), no partner view (decision 3), no atomic multi-row write (decision 5).
- **[ADR-0062](0062-receipt-scan-ocr-prefilled-draft.md) decision 7 is amended** (decision 7 above) and its **decision 9 is confirmed unchanged**, with the reason recorded — the one-hour `cacheDir/scans` sweep never meets a draft.
- **[ADR-0048](0048-confirm-on-arrival-recurring.md) decision 2 reaches the opposite construction here, and both are right.** A pending occurrence is derivable, so it needed no storage at all; a draft is user-authored, so it needed *a* table — and the shared principle ("keep non-money out of the money table's query path") is what selects a *separate* one. Read together, not as a contradiction.
- **A draft is invisible to the partner, to every money calculation, and to the onboarding-emptiness guard** — the three properties the whole design exists to guarantee, and the three the test plan asserts.
- **The cross-device photo gap (decision 4) is the design's known weak point.** If "scan on the phone, settle on the laptop with the receipt visible" later turns out to matter, the fix is a `draft_images` child table — additive, and it does not disturb any other decision here.

## Rejected alternatives (summary)

| Alternative | Why rejected |
|---|---|
| `is_draft` flag on `transactions` | Forces `type`/`amount`/`account_id` nullable on the money table permanently, or placeholder junk values; ~19 `WHERE` clauses with no compile-time membership check; plus an amendment to frozen contract §5.3 for the partner view. Its genuine wins — trivial promotion, and receipt photos syncing for free — did not cover that cost. |
| Local-only drafts (no sync) | Far cheaper — no table, no RLS, no syncer, no contract amendment — but the queue vanishes on a new phone and is invisible on web, which is where "sitting down to settle the queue" most plausibly happens (ADR-0063: web has no local store). |
| Syncing draft photos too | Needs a second synced image child table (`transaction_images` FKs to `transactions`, so it cannot be reused) plus Storage egress for photos with a high abandon rate. |
| A `partner_transaction_drafts` redacting view | A draft is not shared spending (ADR-0011) and may be a half-typed wrong figure or a gift receipt. Also the only option that would have touched frozen contract §5.3. |
| A `SECURITY DEFINER` promotion RPC (contract §9) | Unnecessary: id-preservation makes re-promotion idempotent, so ordering alone gives the needed safety. Building it would buy a backend lift this feature does not need. |
| Separate `filesDir/draft-receipts/` directory | Introduces a file-move failure mode on promotion and duplicates the naming convention, to avoid a one-line change in the sweep's caller. |
| Two tabs (Draft / w/ Receipts) | The second tab filters rather than acting; premium-only in practice, so it needs an upsell empty state on a friction-reduction screen; and hiding it from a lapsed user would violate the freeze policy. |
| A "you have N unfinished drafts" notification | ADR-0048 decision 10 already deferred the equivalent push; needs a new frozen-§1b id format and a new Settings category, for a nag. |
| A `PlanLimits` cap on drafts | Blocks the "just park it" flow that is the feature; contradicts "recording your own money is never gated." |
| Auto-expiring old drafts | Silently deleting user data the app promised to hold. |
