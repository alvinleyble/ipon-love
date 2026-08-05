# Cross-Platform Client Contract (Android ⇄ Web)

**Status: ✅ FROZEN 2026-08-05** (grilled 2026-08-03; fill pass executed as [v1.7.4 Item 1](../build/v1.7.4.md#item-1--freeze-the-cross-platform-contract-from-web-phase-0-w2)). Every section §1–§9 is frozen. **One deliberate, sanctioned exception:** the specific web origin admitted by §7's storage CORS, which cannot be pinned until hosting is chosen ([W8](web-phase-0-prep.md#w8--web-app-greenfield-foundational-design)). Nothing else is left open.

**The load-bearing deliverable is the conformance vectors, not this prose** (W2 grill, 2026-08-03). They live in [`CrossPlatformContractConformanceTest.kt`](../../app/src/test/java/com/iponlove/app/core/util/CrossPlatformContractConformanceTest.kt) and run on every PR. `DeterministicUuid` is *not* `commonMain`-ready — it is Android-free but JVM-bound (`ByteBuffer`, `MessageDigest`, `java.util.UUID`), so the shared-module extraction **rewrites** it onto different SHA-1 and UUID primitives rather than moving it. A rewrite that byte-orders the namespace differently silently produces different ids, i.e. duplicate rows instead of merged ones. The vectors are the only mechanism that catches that, and they must pass before [W10](web-phase-0-prep.md#w10--extract-domaindatasync-layer-into-a-kotlin-multiplatform-shared-module) phase 2.

**Architecture context (2026-07-28, see [W10](web-phase-0-prep.md#w10--extract-domaindatasync-layer-into-a-kotlin-multiplatform-shared-module)):** the web app will share the logic these sections describe via a Kotlin Multiplatform module rather than hand-reimplementing it in TypeScript. For §1/§1b/§2/§3/§4/§5/§6 — once the corresponding logic moves into the shared module — being "reproduced exactly" stops being a discipline problem and becomes a fact of the code (one implementation, two compile targets). This doc is still the authoritative *description* of those invariants (onboarding reference, audit trail, and it governs the period before extraction happens), and it is still the acceptance criteria the extraction is audited against. §7 (image pipeline) and §9 (atomic writes) are the sections that stay genuinely platform-specific even after extraction — those keep the original "must hand-match" stakes.

**What this is:** the single spec that binds every client to identical behavior on the shared Supabase backend. Every rule here fails *silently* when violated — no crash, just duplicated rows or centavo-divergent numbers days later.

**Authority (now inverted, because the sections are frozen):** before freezing, the code was authoritative and this doc described it. **From 2026-08-05 the frozen values are the contract, and any client that drifts — including Android — is the bug.** Two consequences: (a) a discrepancy found later gets **booked as its own fix item**, never quietly edited into this doc; (b) a deliberate change to a frozen value must reckon with rows already written under the old rule (a changed §1 name string does not migrate existing ids — it forks them).

---

## 1. Deterministic UUIDs — the convergence mechanism

*Status: ✅ **FROZEN 2026-08-05**.* Source: [DeterministicUuid.kt](../../app/src/main/java/com/iponlove/app/core/util/DeterministicUuid.kt) + its 6 call sites. Vectors: [`CrossPlatformContractConformanceTest`](../../app/src/test/java/com/iponlove/app/core/util/CrossPlatformContractConformanceTest.kt).

When an id is a deterministic function of stable inputs, two clients independently compute the **same** id, so an upsert collapses the would-be duplicate into one row (and a tombstoned row is never resurrected). Web MUST reproduce this exactly or it *creates* duplicates instead of merging.

### 1.1 Algorithm

- **RFC 4122 name-based version 5 (SHA-1)**, variant bits set. NOT v4 (random), NOT the JDK's `UUID.nameUUIDFromBytes` (v3/MD5), NOT a raw SHA-1 hex string.
- **Namespace UUID:** `9d8f6c2e-5b1a-4f3d-9e7c-1a2b3c4d5e6f`.
- **Exact byte sequence hashed** (this is the part a port gets wrong):
  1. the namespace's **16 raw bytes, most-significant byte first** — i.e. `9d 8f 6c 2e 5b 1a 4f 3d 9e 7c 1a 2b 3c 4d 5e 6f`, the same order the canonical hyphenated string reads left to right. Not the JVM's two `long` halves in native order, not little-endian, not the ASCII of the hyphenated string.
  2. immediately followed by the **UTF-8 bytes of the name**, with no separator, no length prefix, and no trailing NUL.
- **Post-processing:** take the first 16 bytes of the SHA-1 digest, then `byte[6] = (byte[6] & 0x0F) | 0x50` (version 5) and `byte[8] = (byte[8] & 0x3F) | 0x80` (RFC 4122 variant). Render lowercase-hyphenated, big-endian.

The empty-name vector below (`""` → `12e5b210-9978-5686-ae17-0da3b40280bb`) is the purest probe of step 1: nothing but the namespace bytes are hashed, so only a byte-order or namespace-value slip can move it. A port should get this one green first.

### 1.2 Name strings (verbatim, including separators)

| Use | Name string | Source call site |
|---|---|---|
| Recurring occurrence | `{ruleId}:{occurrenceDate}` | `MaterializeRecurringRulesUseCase`, `ConfirmOccurrenceUseCase`, `ObservePendingConfirmationsUseCase` |
| Debt netting payment | `netting:{debtId}:{counterDebtId}` | `DebtNettingCalculator` (**both orderings are generated**, one row per side — ADR-0019 #9) |
| Paid-on-behalf debt | `paid-on-behalf:{transactionId}` | `PaidOnBehalfUseCase` |
| Transfer-fee category | `builtin-category:transfer-fee:{userId}` | `SaveTransferUseCase` (ADR-0031) |
| Starter category | `starter-category:{userId}:{key}` | `SeedStarterDataUseCase` |
| Starter account | `starter-account:{userId}:{key}` | `SeedStarterDataUseCase` |

- **`{occurrenceDate}` is ISO-8601 `yyyy-MM-dd`** — confirmed: the call sites interpolate a `java.time.LocalDate`, whose `toString()` is ISO-8601 local date. Zero-padded (`2026-01-02`, never `2026-1-2`), no time component, no zone suffix. Web must format with an explicit ISO local-date formatter, **not** `Date.toISOString()` (which is UTC-shifted and carries a time) and not a locale-dependent formatter.
- **`{ruleId}`, `{debtId}`, `{transactionId}`, `{userId}` are the canonical lowercase-hyphenated UUID strings** as stored — no uppercasing, no brace/urn forms.
- **Netting generates the pair, not one row.** For an offsetting pair `(new, existing)` the calculator emits a payment on `new` keyed `netting:{new}:{existing}` *and* a payment on `existing` keyed `netting:{existing}:{new}`. Both orderings are live ids; neither is a canonicalization of the other.

### 1.3 Starter `{key}` catalog (frozen — keys are permanent)

From [`StarterCatalog`](../../app/src/main/java/com/iponlove/app/feature/onboarding/domain/model/StarterCatalog.kt). **A key may never be renamed once shipped**: a rename turns an idempotent reseed into a duplicate row, and orphans every already-synced row under the old id.

| Bundle | Keys (in declaration order) |
|---|---|
| Everyday spending | `food`, `groceries`, `transport`, `shopping` |
| Bills & utilities | `rent`, `electricity`, `water`, `internet`, `phoneload` |
| Income | `salary`, `business`, `gift` |
| Reimbursables | `reimbursable`, `reimbursement` |
| Accounts | `cash`, `gcash`, `bank` |

The *display name, icon, colour and position* of a starter item are ordinary row data and may change; only the `key` is frozen. Note `electricity` (key) vs. "Electricity" (name) and `phoneload` (key) vs. "Phone Load" (name) — the key is not derivable from the name.

### 1.4 Conformance vectors

All computed with **`{userId}` = `22222222-2222-4222-8222-222222222222`**, `{ruleId}` = `11111111-1111-4111-8111-111111111111`, `{transactionId}` = `33333333-3333-4333-8333-333333333333`, `{debtIdA}` = `aaaaaaaa-0000-4000-8000-000000000001`, `{debtIdB}` = `bbbbbbbb-0000-4000-8000-000000000002`. Asserted by `CrossPlatformContractConformanceTest`.

| Name string | Expected UUID |
|---|---|
| *(empty string)* | `12e5b210-9978-5686-ae17-0da3b40280bb` |
| `11111111-1111-4111-8111-111111111111:2026-08-05` | `36cb0753-0599-5f1a-96c8-134d33b3bb7d` |
| `netting:aaaaaaaa-0000-4000-8000-000000000001:bbbbbbbb-0000-4000-8000-000000000002` | `45018764-91e5-5ded-87f8-c2640b685be4` |
| `netting:bbbbbbbb-0000-4000-8000-000000000002:aaaaaaaa-0000-4000-8000-000000000001` | `fef252c2-a279-5c95-a2f5-ef842a1f177a` |
| `paid-on-behalf:33333333-3333-4333-8333-333333333333` | `16b41f0d-3057-5e48-9874-5575443dd715` |
| `builtin-category:transfer-fee:{U}` | `e10af228-a1c9-5e68-82ef-2cdeea8ed57f` |

Starter catalog, with `{U}` = `22222222-2222-4222-8222-222222222222`:

| Name string | Expected UUID |
|---|---|
| `starter-category:{U}:food` | `71ed9c9d-0291-5fcd-86d6-f6bf71e25559` |
| `starter-category:{U}:groceries` | `316bc914-9c0d-5c50-abfa-c6e752716118` |
| `starter-category:{U}:transport` | `1808c6d7-2915-5076-af8a-e6ce07395816` |
| `starter-category:{U}:shopping` | `6dd43d60-6fbe-5bc7-b4ba-885221485848` |
| `starter-category:{U}:rent` | `e8e3374a-6595-5e44-9b22-e359a8e3452f` |
| `starter-category:{U}:electricity` | `31067ccd-92b9-5863-b874-3a7aa14d6b17` |
| `starter-category:{U}:water` | `b246f802-6f56-56bf-b26e-c9965b2e5a63` |
| `starter-category:{U}:internet` | `d63599b0-8954-5c8c-89f0-9f88e0f1a4e0` |
| `starter-category:{U}:phoneload` | `8abc2ec4-a21d-5288-b51e-3cf2a0986e5e` |
| `starter-category:{U}:salary` | `0fa355e5-37a2-55fd-8282-f9ada1aee216` |
| `starter-category:{U}:business` | `627bd0a2-bce1-59f9-b6d2-d7fd860e524a` |
| `starter-category:{U}:gift` | `9d5d6838-56b0-5a9a-beaa-bcc869f4d9d9` |
| `starter-category:{U}:reimbursable` | `5b6f73d7-ff39-5234-bc39-83e47a72b4b0` |
| `starter-category:{U}:reimbursement` | `9cf70cce-2110-56ee-ad62-fa6e68316a8c` |
| `starter-account:{U}:cash` | `1d169077-c848-51bf-b021-9d375d3df692` |
| `starter-account:{U}:gcash` | `8f030d9a-44c4-5988-a6ee-d1a5bcc9a520` |
| `starter-account:{U}:bank` | `7c8097c3-a12e-5e5e-8317-67c9117479b6` |

### 1.5 What is deliberately NOT deterministic

Everything else uses a random v4 UUID: ordinary transactions, accounts, categories, budgets, notes, savings goals, manual debt payments, the transfer's own fee row (`SaveTransferUseCase` uses `UUID.randomUUID()` for the fee *transaction*; only the fee *category* is deterministic), and note conflict copies (§5). Do not "helpfully" extend determinism to these — two clients creating two genuinely different expenses must produce two rows.

### 1b. Notification-inbox composite ids — a second, distinct scheme

*Status: ✅ **FROZEN** (built + live, v1.7.1 Item 6 / ADR-0053; adopted as-is, no grill needed).*

The synced notification inbox (`notifications` table, `supabase/schema.sql`, migration #22, Room v30) does **not** use v5-UUID hashing — `id` is a **plain TEXT deterministic composite key**, built client-side from the event itself:

| Category | Id format | Notes |
|---|---|---|
| Budget alert | `budget:{budgetId}:{yyyy-MM}:{slot}` | `slot ∈ {warn, limit, over}` (lowercase) — amended from an earlier `{threshold}` design (ADR-0054) specifically so per-device threshold values stay duplicate-safe across clients. Builder: `CheckBudgetAlertsUseCase.notificationId`. |
| Recurring reminder | `recurring:{occurrenceId}` | `{occurrenceId}` is the §1 deterministic occurrence UUID, so it is stable across clients. |
| Partner debt | `debt:{debtId}` | |

Web MUST build these exact strings so a phone-detected and a web-detected instance of the same event **merge into one row**, not two.

- **Generation is create-if-absent, never upsert-over** — `record()` is `@Insert(IGNORE)` (returns −1 on conflict). Re-detecting an already-recorded event must never clobber its `is_read`/dismissed state. Web's write path must use `INSERT … ON CONFLICT DO NOTHING`, not a plain upsert.
- **The row's existence *is* the dedup record** — it retired the earlier local `BudgetAlertStore`. Don't build a second, web-local dedup store; trust the shared row.
- **Retention is a sanctioned exception to tombstone-only (ADR-0010):** rows older than **60 days** are genuinely hard-deleted by a client sweep. Safe only because every client computes the identical cutoff (`now() − 60d`, not per-user timezone-shifted). Web's sweep (if it runs one) must use the same rule. Ordinary user dismiss/clear-all stays an ordinary soft-delete that syncs normally.
- **Own-user-only, no partner variant, never replicated** — RLS is own-row (`for all using (user_id = auth.uid())`); there is no redacting view. A couple-relevant event (e.g. "partner logged a debt") is generated **independently by each client** from an already-synced base row, landing as the *recipient's own* inbox row — never copied cross-user.
- See **W9** in [web-phase-0-prep.md](web-phase-0-prep.md#w9--evaluate-server-side-notification-generation-explicitly-deferred-by-adr-0053) for the piece of ADR-0053 explicitly deferred to the web build.

---

## 2. Last-Writer-Wins write rule

*Status: ✅ **FROZEN 2026-08-05**.* Source: ADR-0001/0002, [`SyncClock`](../../app/src/main/java/com/iponlove/app/core/sync/SyncClock.kt), [`ClockOffsetStore`](../../app/src/main/java/com/iponlove/app/core/sync/data/ClockOffsetStore.kt), `supabase/schema.sql` header.

### 2.1 The stamp

```
updated_at = max(now() + clockOffset, previousUpdatedAt + 1ms)
```

Exact semantics, as implemented in `SyncClock.stamp(previousUpdatedAt)`:

1. `corrected = deviceWallClockNow() + clockOffsetMillis` (offset added, not subtracted).
2. `floor = previousUpdatedAt + 1 millisecond`, or absent for a brand-new row.
3. **Result = `floor` if `floor` is *strictly after* `corrected`, else `corrected`.** A tie (`floor == corrected`) yields `corrected`. The bump is exactly +1ms — never +1 tick, never a nanosecond, never "current + 1".
4. `previousUpdatedAt` is the **local row's own current `updated_at`**, read immediately before the write. It is per-row: there is no global monotonic counter, and two different rows written in the same millisecond legitimately share a timestamp.
5. A brand-new row passes no previous value and simply takes `corrected`.
6. **Millisecond precision is the contract.** `timestamptz` stores microseconds and `Instant` holds nanoseconds; the bump granularity is 1ms, so a client must not stamp sub-millisecond values that could make a +1ms bump fail to separate two versions. Web must truncate to milliseconds (JS `Date` already is).

**No database trigger ever overrides `updated_at`** — it is deliberately client-authoritative (ADR-0001). The one server-side floor is inside `set_self_entitlement` (§8), which applies `greatest(p_updated_at, current)` so a skewed client cannot move the key backwards.

### 2.2 `pending_sync`

- Set `true` on **every** local write. It is the push-selection outbox flag.
- **Local-only.** It is deliberately absent from `supabase/schema.sql` and must never be sent to or read from the server as authority. Pulled rows land with `pending_sync = false`, so a pull never makes a row dirty and can never trigger a push (no ping-pong).
- Cleared only after the server acknowledges the row's push.
- **Web (online-only, ADR-0063) has no `pending_sync` equivalent and no outbox** — it writes straight through. This section's rule 2.1 still binds it: the timestamp it sends is still the LWW key both clients race on.

### 2.3 Clock-offset acquisition and refresh

- **Source:** the `get_server_time()` RPC — `returns timestamptz`, `language sql`, `security definer`, body `select now()`. It exists solely for this.
- **Computation:** `offsetMillis = serverNow.toEpochMilli() − localNow.toEpochMilli()`, where `localNow` is sampled as close to the response as possible. **Round-trip latency is not compensated** (no Cristian's-algorithm halving) — the offset therefore carries up to one round-trip of positive bias. Frozen as-is: the offset only needs to be accurate to well under the human interval between two partners editing the same row, and both clients bias the same direction.
- **Cadence:** recalibrated at the end of **every successful full sync** (`SyncEngine.sync()`), after push and pull. Deliberately **not** on the narrow `pushOnly()` / `pullOnly()` paths — those are debounced micro-syncs and must not add a round trip. There is no separate timer.
- **Persistence:** held in memory for a synchronous, allocation-light write hot path (`AtomicLong`); persisted to DataStore after each calibration and restored into the clock once on app start. **Startup default is `0`** — a first-ever launch stamps with the raw device clock until the first sync lands.
- **Web equivalent of `ClockOffsetStore`:** `localStorage` (or an in-memory value for a session-only client) holding the same single integer under the same semantics. Web is online-only, so it may also simply call `get_server_time()` at session start; what it must **not** do is skip the offset.

### 2.4 The #1 web pitfall

**Writing a naive `new Date()` / `Date.now()` timestamp corrupts LWW ordering for *both* clients.** A browser clock 4 minutes fast makes every web edit permanently beat every phone edit of the same row, including older ones; a slow clock makes web edits silently lose and appear not to save. This is invisible until a couple edits a shared row from both surfaces. Whatever else web reimplements, it must go through the shared `SyncClock` (or an exact reimplementation of §2.1 + §2.3).

---

## 3. FK push/pull order

*Status: ✅ **FROZEN 2026-08-05**.* Source: [`SyncTable`](../../app/src/main/java/com/iponlove/app/core/sync/SyncTable.kt) (the enum's declaration order *is* the contract), [`SyncEngine`](../../app/src/main/java/com/iponlove/app/core/sync/SyncEngine.kt), ADR-0009/0002.

### 3.1 The single authoritative full ordering

Twenty-three entries, in `SyncTable` ordinal order. **This supersedes any partial list elsewhere** — including ADR-0009's original nine-table list and the "newer leaves appended at the end" shorthand, both of which are now known to be *out of date* rather than merely abbreviated (`transaction_images` sits before `budgets`; the debt tables sit before `notes`).

| # | `SyncTable` | Kind |
|---|---|---|
| 1 | `USERS` | owned (FK root, ADR-0013) |
| 2 | `COUPLES` | RPC-written (§5), pulled like any table |
| 3 | `ACCOUNTS` | owned / flip-model |
| 4 | `CATEGORIES` | owned / flip-model |
| 5 | `RECURRING_RULES` | owned |
| 6 | `TRANSACTIONS` | owned |
| 7 | `TRANSACTION_IMAGES` | owned (child of transactions) |
| 8 | `BUDGETS` | owned / flip-model |
| 9 | `PARTNER_DEBTS` | couple-owned (**not** a redacting view) |
| 10 | `DEBT_PAYMENTS` | couple-owned (child of partner_debts) |
| 11 | `NOTES` | owned |
| 12 | `NOTE_IMAGES` | owned (child of notes) |
| 13 | `SAVINGS_GOALS` | owned |
| 14 | `GOAL_CONTRIBUTIONS` | owned (child of savings_goals) |
| 15 | `PARTNER_ACCOUNTS` | redacting view (pull-only) |
| 16 | `PARTNER_CATEGORIES` | redacting view (pull-only) |
| 17 | `PARTNER_TRANSACTIONS` | redacting view (pull-only) |
| 18 | `PARTNER_TRANSACTION_IMAGES` | redacting view (pull-only) |
| 19 | `PARTNER_NOTES` | redacting view (pull-only) |
| 20 | `PARTNER_NOTE_IMAGES` | redacting view (pull-only) |
| 21 | `PARTNER_SAVINGS_GOALS` | redacting view (pull-only) |
| 22 | `PARTNER_GOAL_CONTRIBUTIONS` | redacting view (pull-only) |
| 23 | `NOTIFICATIONS` | owned leaf, own-user-only (§1b) |

Notes on the shape, so a port doesn't "tidy" it:

- **`PARTNER_DEBTS` / `DEBT_PAYMENTS` are named for the `partner_debts` tables and are ordinary read/write couple-owned tables** — they are *not* the redacted `PARTNER_*` views at #15–#22, despite the name collision. Getting this wrong makes debts read-only on web.
- **The partner views are a contiguous block after every owned table**, not individually interleaved after each owned counterpart. Both satisfy "parents first"; the block form is what is implemented and therefore what is frozen.
- **`NOTIFICATIONS` is last** because nothing depends on it and it must never delay a financial row's push (ADR-0053).
- **Intra-table ordering is out of scope.** §3 is table-level only. The parents-before-children clause that the subcategories item briefly proposed for `categories` is **not** part of this freeze (that item was deferred the same day); it is preserved in ADR-0061 decision 7 and gets re-added only if that item is un-deferred.

#### ⬜ Booked amendment — a 24th entry, `TRANSACTION_DRAFTS` (not yet in code)

**Design-locked 2026-08-05, captain-approved, unbuilt.** [ADR-0066](../adr/0066-transaction-drafts-parking-area.md) / [v1.7.3 Item 8](../build/v1.7.3.md#item-8--transaction-drafts-a-parking-area-inside-records-third-exit-from-the-new-transaction-form) adds a `transaction_drafts` table, which becomes **`SyncTable` #24, appended after `NOTIFICATIONS`** — a pure append, so **ordinals 1–23 above are unchanged**. It earns the same last-place argument `NOTIFICATIONS` has: nothing depends on it, and it must never delay a financial row's push.

**The table above stays at 23 entries until the code exists** — this section's source of truth is the enum's declaration order, and per this document's own rule a not-yet-built change is *booked*, never edited quietly into a frozen list. Move the row into the table, delete this block, and update the conformance checklist when Item 8 Slice 1 lands.

For a porting client: `transaction_drafts` is **owned, own-user-only** (RLS `user_id = auth.uid()`, no redacting view — a partner can never see a draft), rows carry ordinary **v4 random** ids per §1.5, and **promotion to a real transaction writes the `transactions` row *before* retiring the draft** (the draft's id is the transaction's id, so a re-run is an idempotent upsert; the reverse order loses data). §1, §1b, §5.3 and §9 are unaffected — checked, not overlooked.

### 3.2 Push

- **Sequential, in the order above.** A parent must land on the server before its child, or RLS/FK rejects the child.
- **Per-table failure isolation:** one table's rejected push does not abort the run. The first failure is remembered, the remaining tables still push, the pull phase still runs, and the error is rethrown at the end so retry semantics are unchanged. This is load-bearing — a poisoned row that aborted the run early would wedge every subsequent sync forever.
- Push selection is `pending_sync = true`. Partner-view syncers never push (their `push()` is a hard `false`).
- **Flip-model pushability guard** (`isLocallyPushable` / `isCoupleRowPushable`): a dirty row of `accounts` / `categories` / `budgets` is pushed only if it is the user's own personal row (`user_id == me`) or a couple row of the user's *current* couple; `partner_debts` / `partner_debt_payments` rows are pushed only when their `couple_id` is the current couple. Rows failing this (partner-owned, or stamped with a dissolved couple) are skipped as benign local orphans rather than sent — sending them fails the whole table's atomic upsert batch.

### 3.3 Pull

- **Pull is executed in parallel across all tables**, not in FK order. This is a deliberate, frozen deviation from ADR-0009's letter: Room has no entity-level FK constraints, so parallel upserts are safe, and on a steady-state sync most tables return zero rows — serialising ~20 round trips cost ~10s where the parallel batch costs ~1s. **The ordering guarantee in §3.1 is therefore a *push* guarantee.** A client whose local store *does* enforce referential integrity must pull in §3.1 order instead; a client with no local store (web, ADR-0063) is unaffected either way.
- **Cursor rule:** per-table, `server_rev > cursor`, ordered by `server_rev` ascending, paged at **500 rows**; loop until a short page. `server_rev` is a `bigint` from **one global sequence** stamped by a trigger on every insert/update — it orders rows by *server receipt* and is entirely separate from `updated_at` (which decides *who wins*).
- **Cursor advance is post-commit:** apply the batch to the local store first, *then* advance the cursor to the batch's max `server_rev`. An interrupted sync re-pulls rather than skips. Upserts are idempotent by `id`, so the redo is a no-op.
- **A fresh client starts every cursor at 0** and pulls full history (server-side tombstone filtering per ADR-0010).
- **Cursor resets are a real protocol event**, not just cleanup: sign-out / account-switch resets *every* cursor to 0 (ADR-0021), and unpair resets the eight `PARTNER_*` cursors to 0 (§5.3) so a new partner's lower `server_rev` history is not skipped.

---

## 4. Money representation & derived math

*Status: ✅ **FROZEN 2026-08-05**.* Source: `supabase/schema.sql` (numeric columns), [`IponSerializers.kt`](../../app/src/main/java/com/iponlove/app/core/network/serializers/IponSerializers.kt), ADR-0007/0036/0055. Vectors: `CrossPlatformContractConformanceTest`.

### 4.1 Representation

- **Canonical storage:** `numeric(14,2)` on every money column — exact decimal, 2 places, PHP only (no multi-currency).
- **Wire format:** JSON **number** (`BigDecimalSerializer` declares `PrimitiveKind.DOUBLE`; PostgREST returns `numeric` as a JSON number). JS `Number` is the same IEEE-754 double, so web is naturally consistent with the wire *for `numeric(14,2)` magnitudes*.
- **Arithmetic must never be done in native floats.** Web MUST use a decimal library (`decimal.js`, or the shared module's `kotlin-bignum` once W10 lands) for every computation below. The wire being a double is not permission to add doubles: `0.1 + 0.2` diverging in the 17th place is harmless until it crosses a `>=` threshold in a budget rung or a "settled" check, at which point one client shows a debt as settled and the other does not.
- **Domain amounts are positive magnitudes**; direction is carried by `TransactionType`, never by a negative amount (ADR-0007).
- **Nothing derived is ever stored or synced** — balances, budget spend, goal progress, debt remaining and every aggregate are recomputed per client from the ledger. Two clients that round differently therefore show different numbers for identical rows, with nothing to reconcile against.

### 4.2 Per-site scale + rounding

| Derived site | Operation | Scale / rounding | Source |
|---|---|---|---|
| Account balance (ADR-0007) | `opening_balance` + Σ signed ledger postings | **Exact addition, no rounding step.** INCOME `+`, EXPENSE `−`, TRANSFER `−` source / `+` destination. Scale is whatever the inputs carry (2 in practice). | `AccountBalanceCalculator` |
| Net assets | Σ of the above across accounts | Exact addition | `ObserveNetAssetsUseCase` |
| Budget spent | Σ EXPENSE rows in the month/category, excluding `is_settlement` and `is_adjustment` | Exact addition | `BudgetProgressCalculator.spent` |
| Budget effective limit (rollover, ADR-0036) | `amount + (prevLimit − prevSpent)`, chained backward through consecutive months; a missing month breaks the chain (carry resets to 0, never skips the gap); **symmetric — a deficit carries as a negative, never floored at 0** | Exact addition/subtraction | `BudgetProgressCalculator.effectiveLimit` |
| **Budget percent (alerts)** | `spent ÷ amount` at **scale 4, HALF_UP**, `× 100`, then **`toInt()` — which truncates** | scale 4 HALF_UP, then truncate | `CheckBudgetAlertsUseCase` |
| Budget progress bar | `spent.toFloat() / limit.toFloat()`, clamped 0..1 | Float, presentation-only | `BudgetRowsCalculator` |
| Analysis totals (income / expense / net) | Σ over `[startInclusive, endExclusive)`; TRANSFER ignored entirely; `is_settlement` and `is_adjustment` excluded | Exact addition | `AnalysisCalculator` |
| Analysis donut slice fraction | `amount.toFloat() / expense.toFloat()` | Float, presentation-only | `AnalysisCalculator` |
| Analysis pace (avg) | `totalExpense ÷ elapsedBuckets` | **scale 2, HALF_UP** | `FlowMetricsCalculator` |
| Analysis projection | **`avg × bucketCount`**, then `setScale(2, HALF_UP)` — *not* `total × ratio` | scale 2, HALF_UP | `FlowMetricsCalculator` |
| Analysis period comparison | `(current − previous) × 100 ÷ previous`, clamped to ±9999; **null when `previous <= 0`** (not 0, not infinity) | **scale 0, HALF_UP** | `FlowComparisonCalculator` |
| Savings goal saved | Σ non-deleted contributions for the goal; contributions whose goal is absent are ignored | Exact addition | `SavingsGoalCalculator` |
| Savings goal progress | `saved ÷ target` at **scale 4, HALF_UP**, then to Float, clamped 0..1 | scale 4 HALF_UP | `SavingsGoalCalculator` |
| Debt remaining | `amount − Σ payments`, **floored at 0** (`.max(ZERO)` — overpayment never flips negative) | Exact subtraction | `PartnerDebtCalculator` |
| Debt net | Σ remaining, `+` where I borrowed, `−` where I lent; sign decides direction, `SETTLED` at exactly 0 | Exact addition | `PartnerDebtCalculator` |
| Debt netting offset (ADR-0019 #9) | `min(remainingNew, remainingExisting)`, opposing debts FIFO by `created_at` | Exact `min` / subtraction | `DebtNettingCalculator` |
| **Debt overpay allocation (ADR-0055)** | Fill targets **in tick order**, each `min(rest, target.remaining)`; the last one touched absorbs the remainder; targets past exhaustion get **no row at all**; `lump > ceiling` **throws** (blocked in UI, never silently capped); `lump <= 0` throws | Exact subtraction — no rounding is possible, so a split always sums back to exactly the lump | `DebtAllocationCalculator` |
| Transfer fee (ADR-0031) | The fee is a **separate EXPENSE row** for the typed amount under the deterministic "Transfer fees" category (§1.2) — the transfer amount is **not** split or reduced | No arithmetic | `SaveTransferUseCase` |
| Calculator overlay (ADR-0058) | `MathContext(15, HALF_UP)`; `%` is `÷ 100` | 15 significant digits, HALF_UP | `CalculatorEngine` |

**The two easiest sites to get wrong, called out:**

1. **Budget percent truncates after a scale-4 divide.** 79.99% reads **79**, not 80. A port that does `Math.round(spent/amount*100)` fires the 80% alert one transaction early — and because §1b's inbox id is `budget:{id}:{month}:{slot}`, the two clients then race to create the *same* row at different moments rather than diverging visibly.
2. **Projection is `avg × bucketCount`, not `total × (bucketCount / elapsed)`.** These differ by the rounding already baked into `avg`. Frozen as the former.

### 4.3 Worked vectors

Asserted by `CrossPlatformContractConformanceTest`; reproduce these before trusting a port.

| Site | Input | Expected |
|---|---|---|
| Balance | opening `500.00`; INCOME `1000.00`, EXPENSE `249.99`, TRANSFER `100.01` out to `b` (opening `0.00`) | `a = 1150.00`, `b = 100.01` |
| Budget spent | expenses `333.33` + `466.67` in month, `999.99` prior month, income `500.00`, settlement leg `50.00` | `800.00` |
| Budget percent | `800.00 / 1000.00` | `80` |
| Budget percent | `799.90 / 1000.00` | `79` *(0.7999 → 79.99 → truncate)* |
| Budget percent | `1.00 / 3.00` | `33` |
| Budget percent | `2.00 / 3.00` | `66` *(0.6667 → 66.67 → truncate)* |
| Budget percent | `1500.00 / 1000.00` | `150` |
| Pace avg | `1000.00` over 7 elapsed of 31 daily buckets | `142.86` |
| Projection | same | `4428.66` *(142.86 × 31)* |
| Comparison | current `150.00`, previous `100.00` | `+50` |
| Comparison | current `100.50`, previous `100.00` | `+1` *(0.5 → HALF_UP → 1)* |
| Comparison | current `50.00`, previous `0` | `null` |
| Analysis | income `20000.00`; expenses `1234.56` + `765.44`; transfer `5000.00` | income `20000.00`, expense `2000.00`, net `18000.00` |
| Goal progress | saved `10001.00` of `30000.00` | `0.3334` |
| Debt allocation | targets `[d1 500.00, d2 300.00, d3 200.00]`, lump `650.00` | `d1 → 500.00`, `d2 → 150.00`, **no `d3` row**; ceiling `1000.00` |
| Debt allocation | targets `[d1 100.00]`, lump `100.01` | throws (blocked, not capped) |

### 4.4 Timezone is part of money math

Every monthly/periodic aggregate keys on a *local* calendar boundary: `BudgetProgressCalculator` derives a transaction's month via `YearMonth.from(instant.atZone(zone))`, and `Budget.yearMonth` is `YearMonth.toString()` form (`2026-08`). V1 is PH-only and the app passes the system zone, so **web must resolve the same zone the phone would** — a browser in another timezone would sort a `2026-07-31T23:30+08:00` expense into a different budget month than the phone does, and the two clients would then disagree about spend with no row-level conflict to detect. Freeze: **`Asia/Manila` semantics** (i.e. the user's PH-local calendar), not the browser's zone and not UTC.

---

## 5. Conflict resolution & partner data

*Status: ✅ **FROZEN 2026-08-05**.* Source: [`ConflictResolver`](../../app/src/main/java/com/iponlove/app/core/sync/ConflictResolver.kt), [`BasePartnerTableSyncer`](../../app/src/main/java/com/iponlove/app/core/sync/BasePartnerTableSyncer.kt), `supabase/schema.sql` views + policies, ADR-0003/0004/0005/0006/0008/0011/0013.

### 5.1 Row resolution

Given the pulled `remote` row and the `local` row beside it, with `remoteIsNewer = remote.updated_at > local.updated_at` (**strict**):

| Local state | Condition | Resolution |
|---|---|---|
| absent | — | **TakeRemote** (the feed introduces it) |
| clean (`pending_sync = false`) | `remoteIsNewer` | **TakeRemote** |
| clean | else | **KeepLocal** (already held, or a just-pushed equal version) |
| dirty (`pending_sync = true`) | `!remoteIsNewer` | **KeepLocal** |
| dirty, **shared note** | `remoteIsNewer` | **ConflictCopy**, then TakeRemote |
| dirty, anything else | `remoteIsNewer` | **TakeRemote** (local edits are lost — the one lossy case) |

- **Strict `>` is the tie-break**: equal timestamps favour the local row. This keeps an unpushed local edit and makes a re-pull idempotent.
- `server_rev` decides *what to fetch*; `updated_at` decides *who wins*. Never conflate them.
- Applying a remote row clears the local dirty flag for that row.

### 5.2 Note conflict copy (ADR-0003) — the exact algorithm

Triggered **only** when all of: the row is in `notes`, its local copy is dirty, the remote is strictly newer, **and the *remote* row is currently shared** (`isSharedNote` is evaluated on the row being resolved). Then, *before* the remote row is applied:

1. Insert a **new** note with a **fresh random v4 id** (deliberately *not* deterministic — a conflict copy is a genuinely new row).
2. `title = "[Conflict Copy] " + (local.title ?: "")` — literal prefix, one trailing space, empty string for a null title.
3. `content` = the local row's content, verbatim.
4. `is_shared = false`, `couple_id = null` — **the fork is always private to the owner**; it never re-enters the couple's shared surface.
5. `is_pinned = false`, `is_conflict_copy = true`.
6. `user_id` = the local row's owner.
7. `created_at = updated_at = clock.stamp(null)` (§2, no previous-row floor — it is a new row).
8. `is_deleted = false`, `server_rev = null`, `pending_sync = true` — so the fork pushes on the next sync and reaches the other device.
9. **Then** the remote row is upserted over the original id as canonical.

The user therefore ends with both versions: the partner's winning shared note under the original id, and their own edits preserved as a private `[Conflict Copy]` note. **This is the only conflict exception in the app** — every other table is plain LWW.

### 5.3 Partner data

- **Read only through redacting views, never partner base tables** (ADR-0005). The view (a) excludes the caller's own rows server-side, and (b) **nulls out content columns** when a row becomes private/deleted/unshared, so the transition still crosses the wire.
- **Partner syncers are pull-only.** `push()` returns `false` unconditionally; base-table RLS would reject the write anyway.
- **No conflict resolution runs on partner rows** — they are never locally dirty, so LWW is irrelevant. Each pulled row is either purged or upserted.
- **Purge means hard-delete the local replica**, not a soft-delete: partner rows have no tombstone lifecycle of their own.

**Frozen purge predicates** (per table, exactly as implemented):

| Partner table | Purge when |
|---|---|
| `partner_accounts` | `is_deleted` |
| `partner_categories` | `is_deleted` |
| `partner_transactions` | `is_private` **or** `is_deleted` |
| `partner_transaction_images` | `url == null` **or** `is_deleted` (a nulled URL *is* the redaction signal) |
| `partner_notes` | `!is_shared` **or** `is_deleted` |
| `partner_note_images` | `url == null` **or** `is_deleted` |
| `partner_savings_goals` | `!is_shared` **or** `is_deleted` |
| `partner_goal_contributions` | `is_deleted` |

**Unpair purge (the bulk case, ADR-0008).** RLS stops returning partner rows the instant `couple_id` goes null, so per-row removal markers can *never* arrive. The trigger is therefore local: watch the current user's own `users.couple_id` for a **set → null transition** and purge on it. Frozen details:

- The *initial* observed value is recorded, never treated as a transition (an already-paired launch must not purge).
- A `null` **user row** (row gone, e.g. a local wipe) resets tracking rather than counting as a transition — otherwise "paired row → wiped → stub recreated" reads as set→null and spuriously purges.
- Purge covers: partner accounts, categories, transactions, transaction images, notes, note images, **shared budgets**, **couple debts + debt payments**, partner savings goals and partner goal contributions.
- Then **reset the eight `PARTNER_*` pull cursors to 0** — a future pairing's history carries `server_rev` values *below* the old cursor and would otherwise be skipped forever. Shared budgets and debts need no reset (a re-pairing's rows are new, hence higher `server_rev`).
- Own contributions to an ex-partner's goal are left as benign orphans and are ignored by the calculators.

### 5.4 Users row (ADR-0013)

`EnsureCurrentUserRow` runs **once per login, before any other write and before the first sync**: if the local row for the signed-in user is absent, create it (seeding the display name captured at registration) with `pending_sync = true`. **No database trigger creates this row.** It is `SyncTable` #1 so it pushes before every FK child. A client that skips this writes accounts/categories against a missing FK root.

### 5.5 Couple RPCs — signatures and error shapes

**Never write to the `couples` table directly** (ADR-0006/0008). All are `security definer`, `set search_path = public`, invoked via PostgREST `rpc()`.

| Function | Signature | Returns | Raises (message verbatim) |
|---|---|---|---|
| `create_couple` | `(p_name text)` | `uuid` (new couple id) | `already in a couple` |
| `redeem_invite` | `(p_code text)` | `uuid` (joined couple id) | `already in a couple`, `invalid invite code`, `couple is already full`, `cannot join your own couple` |
| `rotate_invite_code` | `()` | `text` (new code) | `no couple to rotate` |
| `set_couple_banner` | `(p_url text)` | `void` | `no couple to update` |
| `unpair` | `()` | `void` | `not in a couple`, `not a member of this couple` |
| `get_server_time` | `()` | `timestamptz` | — |
| `delete_account` | `()` | `void` | *(ADR-0045; hard-delete, the one sanctioned exception to ADR-0010)* |
| `set_self_entitlement` | see §8 | `void` | `invalid entitlement_source: %` |

- **Error transport:** a `raise exception` surfaces through PostgREST as HTTP **400** with a JSON body carrying `code` `P0001` and the `message` above. Web must match on the message string (there are no distinct SQLSTATEs) and map to user-facing copy; it must **not** treat a 400 here as a transport failure to retry.
- **Invite codes** are 6 characters from the alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (32 symbols; `I`, `O`, `0`, `1` deliberately excluded), generated server-side with a uniqueness retry loop. Clients never generate or validate codes locally beyond length/charset.
- **`redeem_invite` re-stamps the inviter's `users` row** (`updated_at = updated_at`, which fires the `server_rev` trigger without disturbing the client-authoritative LWW key) so the redeemer's already-advanced `users` cursor re-fetches the partner row. A client that reimplements pairing must not "optimise" this away.
- **`unpair` also broadcasts a Realtime bell** (`realtime.send('{}', 'changed', 'couple:{id}', private = true)`) from *inside* the transaction — the only server-side broadcast in the schema, necessary because the initiator's `auth_couple_id()` goes null the instant it commits. Payload is empty by design (ADR-0015 redaction): the partner reacts by **pulling** through the redacting views. A web client subscribing to the couple channel must treat any bell as "pull", never as data.

---

## 6. Onboarding / starter-data seeding

*Status: ✅ **FROZEN 2026-08-05**.* Source: [`SeedStarterDataUseCase`](../../app/src/main/java/com/iponlove/app/feature/onboarding/domain/usecase/SeedStarterDataUseCase.kt), [`ShouldShowOnboardingUseCase`](../../app/src/main/java/com/iponlove/app/feature/onboarding/domain/usecase/ShouldShowOnboardingUseCase.kt), `MainActivity`'s launch effect. Absorbs [W5](web-phase-0-prep.md#w5--starter-seeding-guard-parity).

### 6.1 What seeding does

- Writes the selected bundles' categories and accounts with the **§1 deterministic ids**, upserting (overwriting in place). It is idempotent by construction and **does not itself check tombstones** — a re-seed re-creates a row the user deleted, because the deterministic id resolves to that exact row and the upsert un-deletes it.
- **FK order within the seed:** accounts before categories (both children of `users`/`couples`).
- **Category `position` is a running counter across the selected bundles**, in declaration order (Everyday spending → Bills & utilities → Income → Reimbursables), so the list reads cleanly for any subset. Account `position` restarts per call.
- Starter accounts are created with `opening_balance = 0`.
- `reimbursable` / `reimbursement` are seeded with `exclude_from_analysis = true` (ADR-0049).

### 6.2 The guard (the part web must not get wrong)

The re-seed guard lives **upstream** of seeding and is a **server-state** check, not a local-emptiness check:

```
shouldShowOnboarding = syncSucceeded
                    && !onboardingDoneFlag
                    && ownedCategoryCount == 0
                    && ownedAccountCount == 0
```

Precise semantics, all four clauses frozen:

1. **`syncSucceeded` is the launch sync's own return value** — the counts are only meaningful *after* server state has landed. A failed or offline first sync **always defers** (returns false); the decision simply retries next launch. It must be this call's own outcome, not a sampled global sync-state flag.
2. **`onboardingDoneFlag`** is a local per-device DataStore boolean set when the onboarding graph completes. It is a fast-path skip, **not** the real guard.
3. **The counts are of *owned, non-deleted* rows** — `SELECT COUNT(*) … WHERE user_id = me AND is_deleted = 0`. Shared/couple-owned and partner rows do not count. **Tombstones do not count**, which is what makes the guard tombstone-respecting: a user who deleted all their starter rows still has `count == 0`… and *would* be re-onboarded. That is accepted on Android because clause 1 plus clause 2 covers it in practice, and because re-running onboarding is a visible, user-driven flow rather than a silent write.
4. On Android the counts are read from Room *after* a successful sync, which makes them a proxy for server state. **On web there is no local store (ADR-0063), so web MUST issue the equivalent count directly against Supabase** — `select count(*) from categories where user_id = auth.uid() and is_deleted = false`, same for `accounts` — and must **not** substitute "my in-memory cache is empty."

**The failure this prevents:** the web client has empty local state *by definition*. A naive "no rows locally → seed" resurrects every starter category and account the user already deleted on their phone, because §1's deterministic ids upsert straight over the tombstones. This is the single highest-consequence rule in §6.

### 6.3 Frozen web behaviour

**Web MAY run onboarding + seeding, and MUST use the §6.2 guard when it does.** Rejected: "web never seeds, rely on the Android seed syncing down" — that would leave a web-first signup with no categories or accounts at all, and web-first signup is a normal path once the app has a browser surface. Web's `onboardingDoneFlag` equivalent is optional (clause 2 is a fast path); clauses 1, 3 and 4 are mandatory.

---

## 7. Image / attachment pipeline

*Status: ✅ **FROZEN 2026-08-05**, with **one deliberately pending line** (§7.5, the allowed web origin).* Source: `note_images` / `transaction_images` / `couple-banners`, [`StorageAuthInterceptor`](../../app/src/main/java/com/iponlove/app/core/network/StorageAuthInterceptor.kt), [`CompressReceiptUseCase`](../../app/src/main/java/com/iponlove/app/feature/transactions/domain/usecase/CompressReceiptUseCase.kt), ADR-0043. Absorbs [W6](web-phase-0-prep.md#w6--image-pipeline-parity-compression-private-buckets-storage-rls-orphans). **Stays platform-specific after W10** — compression in particular has no shared implementation.

### 7.1 Buckets and object paths

All three buckets are **private**. `folder[1]` is the RLS key in every case, so the path shape is a security boundary, not a convention.

| Bucket | Object path | RLS key | Written by |
|---|---|---|---|
| `receipts` | `{userId}/{transactionId}/{imageId}.jpg` | `folder[1] = auth.uid()` | `TransactionImageUploader` |
| `note-images` | `{userId}/{noteId}/{imageId}.jpg` | `folder[1] = auth.uid()` | `NoteAttachmentUploader` |
| `couple-banners` | `{coupleId}/{randomUuid}.jpg` | `folder[1] = auth_couple_id()` | `CoupleBannerUploader` |

- `{imageId}` is the attachment row's own id (a v4 UUID), so one transaction can carry several receipts (max 3) and one note several images.
- **Couple banners use a fresh random filename on every upload** — deliberately, so a replaced banner yields a new URL and no client serves a stale cached image. Do not make it deterministic.
- Uploads are `upsert = true` (idempotent retry).

### 7.2 Compression parameters

| Source | Max dimension | Format / quality |
|---|---|---|
| Receipt photo | **1080 px** on the longer edge | JPEG, quality **85** |
| Note image | **1080 px** on the longer edge | JPEG, quality **85** |
| Couple banner | **1440 px** on the longer edge | JPEG, quality **85** |

Scaling is aspect-preserving (`ratio = max / max(width, height)`) and **only downscales** — an image already within the bound is passed through untouched. Web should match these targets so a couple's phone-uploaded and browser-uploaded attachments are comparable in size and quality; exact byte-for-byte encoder parity is **not** required (browser JPEG encoders differ from Android's) and is explicitly not part of the contract. What *is* part of the contract: the dimension caps, JPEG, and never uploading an unbounded original.

### 7.3 URL scheme and authenticated access

- Rows store the **authenticated-form URL**, not a path and not a local file path:
  `{SUPABASE_URL}/storage/v1/object/authenticated/{bucket}/{objectPath}`
- **The URL is not a credential.** Buckets are private; a fetch resolves only with `apikey` and `Authorization: Bearer <access token>` headers attached, after which per-object RLS decides access.
- **Legacy `/object/public/` URLs exist in the database** (stamped before the buckets were confirmed private) and always 400. Every client MUST rewrite `/object/public/` → `/object/authenticated/` **at request time** — there was no data migration, and partner-replicated copies carry the legacy form too. Do the rewrite below any image cache so cache keys stay stable.
- Signed URLs are not used anywhere in this pipeline; do not introduce them on one client only.

### 7.4 Storage RLS

- `receipts_owner` / `note_images_owner`: full access where `folder[1] = auth.uid()`.
- `receipts_partner_read` / `note_images_partner_read`: **SELECT only**, gated by the `SECURITY DEFINER` helpers `partner_can_read_receipt(name)` / `partner_can_read_note_image(name)` (ADR-0043). Definer is required because a storage policy runs under the *requesting* user's RLS, and the owner-scoped base tables would be invisible to the partner — every download 400'd before this. The helpers check: attachment not deleted, parent not deleted, parent **not private** (receipts) / **shared** (note images), and parent owned by a member of the caller's current couple other than the caller.
- `couple_banners_rw`: full access where `folder[1] = auth_couple_id()` — either partner reads and writes; the couple-id folder key *is* the gate, so no partner-read helper is needed.
- **Web needs no new storage policy.** The policies key on `auth.uid()` / `auth_couple_id()` from the JWT, which a browser session resolves identically.

### 7.5 CORS / allowed origin — ⬜ **deliberately pending**

Supabase Storage must admit the web app's origin for browser `fetch`/`XHR` of object URLs. **The specific origin cannot be pinned until hosting is chosen** (Vercel vs. Cloudflare Pages — open in [W8](web-phase-0-prep.md#w8--web-app-greenfield-foundational-design)). This is the **sole sanctioned unfrozen line in this document**; it is recorded as pending rather than invented, per the 2026-08-03 grill's scope ruling. Everything else in §7 is frozen. When hosting lands, add the origin (production + preview domains) and mark this line frozen — no other §7 value changes.

### 7.6 Orphan cleanup

- **Upload is a pre-sync step, not part of the row write.** A row is created locally with a `localPath` and no `storage_url`; the uploader runs before each push, uploads, stamps the URL, then deletes the local file. A failure leaves the row untouched for the next sync to retry — it must never fail the whole sync.
- **A soft-deleted, never-uploaded row is hard-deleted locally** (plus its file) rather than pushed — it never needs to reach the server.
- Android additionally sweeps `filesDir/receipts` for compressed files that never got a row. **That is an Android-local concern with no web equivalent** (a browser holds the blob in memory). ⬜ *Booked, unbuilt (2026-08-05, [ADR-0066](../adr/0066-transaction-drafts-parking-area.md) decision 6): that sweep gains a second exclusion set — files referenced by a parked **transaction draft**, which has no `transaction_images` row and would otherwise be deleted before the user returns. Still Android-local: draft receipt photos are deliberately **not** synced (they cross only on promotion), so a web client sees a draft's `receipt_count` but never its file, and has nothing to sweep.*
- **Web's orphan case is the inverse and is web's own to handle:** an object uploaded to Storage whose row insert then fails leaves an unreferenced object. Frozen rule: **upload first, write the row second, and on row-write failure delete the just-uploaded object best-effort.** A leaked object is unreadable to anyone but its owner and is not a data-integrity problem, so best-effort is sufficient; what is *not* acceptable is a row pointing at an object that was never uploaded.
- Couple-banner replacement deletes the previous object best-effort; `unpair()` and `delete_account()` delete the couple's banner objects server-side.

---

## 8. Entitlement (read on web, write locked)

*Status: ✅ **FROZEN 2026-08-05**.* Source: **shipped** [ADR-0060](../adr/0060-entitlement-columns-are-rpc-write-only.md) (v1.7.2 Item 1), [ADR-0044](../adr/0044-entitlement-client-trusted-advisory-column.md), [`subscription-paywall-design.md`](../build/subscription-paywall-design.md). See [W1](web-phase-0-prep.md#w1--lock-the-entitlement-columns-rls--validating-write-rpc) / [W7](web-phase-0-prep.md#w7--web-premium-purchase-path-play-cant-sell-on-web).

### 8.1 Read path

- The four entitlement columns live on the synced `users` row: `is_premium`, `premium_until`, `entitlement_source` (`PLAY | GRANT | NONE`), `entitlement_checked_at`. They pull like any other `users` column.
- **Couple governance:** effective access is `me.active || partner.active` — either partner's Premium unlocks shared surfaces for both.
- **Reading is offline-first and fail-open on cold start** (ADR-0044 §6) — untouched by the lock. Web is online-only, so "offline read" degenerates to "read the synced row"; the fail-open rule still matters for the moment before the row arrives.
- Entitlement never gates sync or visibility. It is advisory. Enforcement ships **dormant** (kill-switch OFF, everything unlocked) and flips only on explicit go.

### 8.2 Write path — the allowlist grant + one RPC

The lock is an **allowlist grant**, not a column-level revoke (a column-level `revoke update (col)` against a table-level grant is a **silent no-op** in Postgres — ADR-0060 §1, verified empirically):

```sql
revoke update on users from authenticated;
grant update (id, display_name, avatar_url, accent_color, avatar_motif,
              couple_id, created_at, updated_at, server_rev) on users to authenticated;
```

**Standing consequence for every client, and the thing most likely to bite silently: adding a column to `users` is now a two-step change** — the column is unwritable by clients until it is added to that grant, and the symptom of forgetting is a field that quietly stops syncing, not an error. The row-level `users_update` policy is unchanged and still applies underneath.

**The sole write path for the four columns:**

```sql
set_self_entitlement(
    p_is_premium    boolean,
    p_premium_until timestamptz,
    p_source        text,          -- 'PLAY' | 'GRANT' | 'NONE'
    p_checked_at    timestamptz,
    p_updated_at    timestamptz
) returns void   -- security definer
```

Frozen server-side behaviour:

1. `p_source` outside `PLAY | GRANT | NONE` → raises `invalid entitlement_source: %`.
2. No `users` row for the caller → **returns silently** (a brand-new signup's row is created by the ordinary upsert with column defaults).
3. **A `GRANT` row is never downgraded by a non-`GRANT` write** — a beta comp survives any client reconcile. This rule moved from client convention into the database precisely because the web client will have its own reconcile loop (ADR-0060 §4).
4. **No-change early return:** if `is_premium`, `premium_until` and `entitlement_source` all match, nothing is written. Load-bearing — the RPC is called on *every* dirty `users` push, so without it an accent-colour edit would bump `server_rev` and make the partner re-pull the row every time.
5. `updated_at = greatest(p_updated_at, current)` — the one server-side floor on the client-authoritative LWW key (§2), so a skewed client cannot move it backwards.
6. Granted to `authenticated`.

### 8.3 Client obligations

- **The ordinary `users` push must NOT name the entitlement columns.** The push is a full-row upsert, and Postgres' column-privilege check keys on a column being *named*, not on its value changing — so shipping them would break **every ordinary profile edit**, not just spoof attempts. Android splits this into `UserPushDto` (profile columns only) plus a `set_self_entitlement` call; web must do the same.
- **Both halves go out together on one push, and both are idempotent.** The upsert runs first (so a new signup's row exists before the RPC looks for it); if either throws, the whole push throws, `pending_sync` is never cleared, and the row retries. On Android this preserves offline-first — the local write and dirty flag are unchanged, only the push *target* moved.
- **Web's purchase flow is not designed yet ([W7](web-phase-0-prep.md#w7--web-premium-purchase-path-play-cant-sell-on-web))**, but its landing point is frozen: whatever sells Premium on web writes entitlement **through this RPC and no other path**. That is not an open question.

### 8.4 What this does *not* do — stated plainly

**The RPC is a passthrough. It validates nothing about the purchase.** A forged call still sets `is_premium = true`. The bar rises from "write any column on your row" to "know the RPC exists and call it correctly" — a real but modest improvement. **ADR-0060 closed the door; it did not lock it.** The lock is Play receipt validation (deliberately deferred, needing an edge function shared with AI credit metering), and it **must land before** any of: enforcement flip-day, the web purchase path (W7), or AI. Do not read §8 as "entitlement is secured."

---

## 9. Atomic multi-row writes — Android has a primitive web doesn't

*Status: ✅ **FROZEN 2026-08-03** (W2 grill) — grouped multi-row settlement stays Android-only; the forward-looking rule below is the binding part.* Source: v1.7.1 Item 10 / ADR-0055, `LocalTransactionRunner`; decision recorded in [ADR-0063](../adr/0063-web-v1-is-online-only.md).

Android's debt-overpay cascade (2026-07-28) became the app's **first true atomic multi-row write** — one EXPENSE transaction + N `DebtPayment` rows must commit all-or-nothing, since a partial write would leave the ledger overstating what reached the debts. It's implemented via `RoomDatabase.withTransaction`, wrapped behind a small `LocalTransactionRunner` seam (originally built for Reset-finances, ADR-0037; reused here with no new infra).

**This has no client-side equivalent on web.** `@supabase/supabase-js` issues independent REST calls per table — there is no client-side "wrap these N inserts in one transaction" primitive against PostgREST. If web ever needs the same all-or-nothing guarantee (this feature, or a future one shaped like it), the write must move **server-side**: a Postgres `SECURITY DEFINER` RPC that performs the whole multi-row write in one SQL transaction, called once from the client. That's a different shape than the Android implementation (client-orchestrated vs. server-orchestrated) — so this is *not* just "port the Kotlin logic to TypeScript."

**✅ Decision (2026-08-03 grill): leave it Android-only. Do not build the RPC now.**

The question turned out to depend on an upstream one — whether web has any local database at all — which the same grill settled as **online-only** ([ADR-0063](../adr/0063-web-v1-is-online-only.md)). With no browser-local store, web cannot mirror Android's local-transaction approach, so authoring grouped settlements on web would require the `SECURITY DEFINER` RPC. That was judged not worth building yet:

- **The gap is convenience, not correctness.** ADR-0055's own analysis establishes that the money end-state is identical no matter how a lump is spread across same-direction debts — only *which labeled debt reads as paid* changes. A web user settling three debts one at a time reaches exactly the same financial position, losing the single grouped expense line and the tick-order choice, nothing more.
- **Android must not switch to the RPC either.** Android is offline-first; routing this write through a server function would require connectivity at settle time, which is a regression, not a unification. So "one implementation for both clients" is not actually on the table here — only "Android-only now" or "two implementations."
- **Web still reads grouped settlements correctly** — the rows are ordinary transactions and debt payments; only authoring is unavailable.

**Frozen forward-looking rule (the binding part):** any *future* feature requiring an all-or-nothing write across multiple tables **must be designed as a server-side `SECURITY DEFINER` RPC from the start if web needs to author it**. The Android `RoomDatabase.withTransaction` pattern does not port to a browser client, so "build it Android-style and port later" is never available for that class of feature — the port is a re-architecture, not a translation.

**Revisit trigger:** web needing to author grouped settlement (or any second all-or-nothing feature) is what justifies building the RPC; at that point expect two implementations (Android local transaction + server RPC) and budget for keeping them consistent.

- **Ties to:** [W8](web-phase-0-prep.md#w8--web-app-greenfield-foundational-design) (its offline-first tension, now resolved), [ADR-0063](../adr/0063-web-v1-is-online-only.md).

---

## Conformance checklist

- [x] **§1 Deterministic UUIDs** — ✅ FROZEN 2026-08-05: v5/SHA-1, namespace `9d8f6c2e-…`, MSB-first namespace bytes + UTF-8 name, all six name schemas, the 17-key starter catalog, and 23 name→UUID vectors asserted in `CrossPlatformContractConformanceTest`.
- [x] **§1b Notification-inbox composite ids** — ✅ FROZEN (already live; adopted as-is): `budget:{id}:{yyyy-MM}:{slot}` / `recurring:{occurrenceId}` / `debt:{debtId}`, create-if-absent, 60-day hard-delete sweep.
- [x] **§2 LWW write rule** — ✅ FROZEN 2026-08-05: `max(now + offset, prev + 1ms)` with strict-after tie-break and ms granularity; offset from `get_server_time()`, recalibrated after every full sync, persisted, default 0; `pending_sync` local-only.
- [x] **§3 FK order** — ✅ FROZEN 2026-08-05: the 23-entry `SyncTable` ordering (supersedes ADR-0009's nine); push sequential with per-table failure isolation, pull parallel; per-table `server_rev > cursor`, page 500, post-commit advance. ⬜ *One booked, unbuilt amendment: a 24th entry (`TRANSACTION_DRAFTS`) appended after `NOTIFICATIONS`, ordinals 1–23 unchanged — see §3.1.*
- [x] **§4 Money math** — ✅ FROZEN 2026-08-05: `numeric(14,2)`, decimal-library mandate, per-site scale/rounding table (incl. the truncating budget percent and `avg × buckets` projection), PH-local calendar boundaries, and 16 worked vectors.
- [x] **§5 Conflict resolution** — ✅ FROZEN 2026-08-05: LWW with strict-`>` tie-break; the 9-step note conflict-copy algorithm; the eight partner purge predicates; the set→null unpair purge + cursor reset; `EnsureCurrentUserRow`; all eight RPC signatures with verbatim error messages.
- [x] **§6 Seeding** — ✅ FROZEN 2026-08-05: the four-clause `shouldShowOnboarding` predicate, owned-and-non-deleted counts read from *server* state, web may seed but must use the guard.
- [x] **§7 Images** — ✅ FROZEN 2026-08-05: three bucket/path conventions with `folder[1]` as the RLS key, 1080/1440 px + JPEG 85, authenticated-URL scheme with the legacy `/public/` rewrite, RLS map, upload-then-row orphan rule. ⬜ *The allowed web origin for storage CORS remains deliberately pending the hosting choice (§7.5) — the sole sanctioned exception.*
- [x] **§8 Entitlement** — ✅ FROZEN 2026-08-05: the ADR-0060 allowlist grant (and its standing two-step cost for new `users` columns), the `set_self_entitlement` signature + its six frozen behaviours, the client's split-push obligation, and the explicit statement that the RPC is an unvalidated passthrough.
- [x] **§9 Atomic multi-row writes** — ✅ FROZEN 2026-08-03: Android-only, no RPC now, plus the forward rule that future all-or-nothing features are server-side from the start ([ADR-0063](../adr/0063-web-v1-is-online-only.md)).

### Discrepancies found while freezing (booked, not fixed here)

Per this item's rule that "changing Android behavior to match a newly-written contract value is out of scope — discrepancies get booked, not fixed inline," the fill pass found **no behavioural discrepancy** requiring an Android change. It did find three places where *prior documentation* had drifted from the code; those are corrected in this document (which is now authoritative) rather than booked:

1. **ADR-0009's table order is stale** — `transaction_images` sits before `budgets`, and the debt tables before `notes`; the newer leaves were never simply "appended at the end." §3.1 is now the single authoritative list.
2. **Pull is parallel, not FK-ordered** — a deliberate performance change that made ADR-0009's "both directions" wording literally untrue. §3.3 records the ordering as a *push* guarantee.
3. **The §6 guard is "server state via a synced local store," not a server query** — an important distinction for a client with no local store, and the reason §6.2 clause 4 is spelled out explicitly for web.
