# Cross-Platform Client Contract (Android ⇄ Web)

**Status: GRILLED 2026-08-03, FILL PASS PENDING.** [W2](web-phase-0-prep.md#w2--freeze-the-cross-platform-contract-determinism--sync-protocol--money-math)'s grill settled the two questions that needed a judgment call — **§9** (frozen below: Android-only, plus the forward rule) and the **§7 scope ruling** (everything freezes except the allowed web origin). The remaining sections need no further decisions; they are **code archaeology** — read the cited Android sources, write down what is already true, and produce conformance vectors. Sections still marked `⬜` are stubs carrying the questions each must answer and pointers to the authoritative source. Do **not** treat any value here as final until its section is marked ✅ FROZEN.

**The load-bearing deliverable is the conformance vectors, not this prose** (W2 grill, 2026-08-03). `DeterministicUuid` is *not* `commonMain`-ready — it is Android-free but JVM-bound (`ByteBuffer`, `MessageDigest`, `java.util.UUID`), so the shared-module extraction **rewrites** it onto different SHA-1 and UUID primitives rather than moving it. A rewrite that byte-orders the namespace differently silently produces different ids, i.e. duplicate rows instead of merged ones. The vectors are the only mechanism that catches that, and they must exist before [W10](web-phase-0-prep.md#w10--extract-domaindatasync-layer-into-a-kotlin-multiplatform-shared-module) phase 2.

**Architecture update (2026-07-28, see [W10](web-phase-0-prep.md#w10--extract-domaindatasync-layer-into-a-kotlin-multiplatform-shared-module)):** the web app will share the logic these sections describe via a Kotlin Multiplatform module, rather than hand-reimplementing it in TypeScript. **This doc still matters, but its job changes:** for §1/§1b/§2/§3/§4/§5/§6 — once the corresponding logic moves into the shared module — being "reproduced exactly" stops being a discipline problem (a human/AI carefully matching a spec) and becomes a fact of the code (one implementation, two compile targets). This doc is still the authoritative *description* of those invariants (onboarding reference, audit trail, and it still governs the period before extraction happens), but it is no longer the last line of defense against drift for whatever gets shared. §7 (image compression) and §9 (atomic writes) are the sections most likely to stay genuinely platform-specific even after extraction — those keep the original "must hand-match" stakes.

**What this is (for whatever isn't/can't be shared, and until W10 is built):** the single spec that binds every client to identical behavior on the shared Supabase backend. Where two independent implementations exist, they converge **only** if the second reproduces these rules exactly — every rule here fails *silently* when violated, no crash, just duplicated rows or centavo-divergent numbers days later. This doc is to the multi-client sync layer what `subscription-paywall-design.md` is to the paywall: the frozen reference a second implementation builds against, for as long as a second implementation exists.

**Authority:** where this doc and the code disagree, the code (`supabase/schema.sql` + the cited Android sources) is authoritative *until a section is frozen here* — freezing means we've committed to the value as the contract and any client (incl. Android) that drifts is the bug.

---

## 1. Deterministic UUIDs — the convergence mechanism

*Status: ⬜ not frozen.* Source: [DeterministicUuid.kt](../../app/src/main/java/com/iponlove/app/core/util/DeterministicUuid.kt) + call sites.

When an id is a deterministic function of stable inputs, two clients independently compute the **same** id, so an upsert collapses the would-be duplicate into one row (and a tombstoned row is never resurrected). Web MUST reproduce this exactly or it *creates* duplicates instead of merging.

- **Algorithm:** RFC-4122 **version 5** (SHA-1), variant bits set. NOT v4 (random), NOT the JDK's `nameUUIDFromBytes` (v3/MD5).
- **Namespace UUID:** `9d8f6c2e-5b1a-4f3d-9e7c-1a2b3c4d5e6f` (hashed as its 16 raw bytes, most-significant-first, before the name bytes).
- **Name = UTF-8 bytes** of these exact strings (verbatim, incl. separators):

| Use | Name string | Source call site |
|---|---|---|
| Recurring occurrence | `{ruleId}:{occurrenceDate}` | MaterializeRecurringRulesUseCase, ConfirmOccurrenceUseCase, ObservePendingConfirmationsUseCase |
| Debt netting payment | `netting:{debtIdA}:{debtIdB}` | DebtNettingCalculator (both orderings, ADR-0019 #9) |
| Paid-on-behalf debt | `paid-on-behalf:{transactionId}` | PaidOnBehalfUseCase |
| Transfer-fee category | `builtin-category:transfer-fee:{userId}` | SaveTransferUseCase |
| Starter category | `starter-category:{userId}:{key}` | SeedStarterDataUseCase |
| Starter account | `starter-account:{userId}:{key}` | SeedStarterDataUseCase |

- **To pin at grill:** exact date-string format for `{occurrenceDate}` (ISO `yyyy-MM-dd`? confirm); the full `{key}` list for starter items (from StarterCatalog); byte-order details of the namespace serialization; a shared conformance vector (known name → expected UUID) the web team can unit-test against.

### 1b. Notification-inbox composite ids — a second, distinct scheme (✅ BUILT + live, v1.7.1 Item 6 / ADR-0053)

The synced notification inbox (`notifications` table, `supabase/schema.sql`, migration #22, Room v30) does **not** use v5-UUID hashing — `id` is a **plain TEXT deterministic composite key**, built client-side from the event itself:

| Category | Id format | Notes |
|---|---|---|
| Budget alert | `budget:{budgetId}:{yyyy-MM}:{slot}` | `slot ∈ {warn, limit, over}` — amended from an earlier `{threshold}` design (ADR-0054) specifically so per-device threshold values stay duplicate-safe across clients. |
| Recurring reminder | `recurring:{occurrenceId}` | |
| Partner debt | `debt:{debtId}` | |

Web MUST build these exact strings so a phone-detected and a web-detected instance of the same event **merge into one row**, not two.

- **Generation is create-if-absent, never upsert-over** — `record()` is implemented as `@Insert(IGNORE)` (returns −1 on conflict). This is intentional: re-detecting an already-recorded event must never clobber its `is_read`/dismissed state. Web's write path must use the equivalent (`INSERT ... ON CONFLICT DO NOTHING`), not a plain upsert.
- **The row's existence *is* the dedup record** — it retired the earlier local `BudgetAlertStore`. Don't build a second, web-local dedup store; trust the shared row.
- **Retention is a sanctioned exception to tombstone-only (ADR-0010):** rows older than **60 days** are genuinely hard-deleted by a client sweep, not soft-deleted — safe only because every client computes the identical cutoff (`now() - 60d`, not per-user timezone-shifted). Web's sweep (if it runs one) must use the same cutoff rule. Ordinary user dismiss/clear-all stays an ordinary soft-delete that syncs normally.
- **Own-user-only, no partner variant, never replicated** — RLS is own-row (`for all using (user_id = auth.uid())`); there is no redacting view. A couple-relevant event (e.g. "partner logged a debt") is generated **independently by each client** from an already-synced base row (the debt), landing as the *recipient's own* inbox row — it is never copied cross-user.
- **This table was deliberately built *now*, not deferred, specifically because the web app is imminent** (ADR-0053's own stated rationale) — it's the one piece of this contract that's already proven out, not speculative. See also **W9** in [web-phase-0-prep.md](web-phase-0-prep.md#w9--evaluate-server-side-notification-generation-explicitly-deferred-by-adr-0053) for the piece of this ADR-0053 explicitly deferred to the web build.

---

## 2. Last-Writer-Wins write rule

*Status: ⬜ not frozen.* Source: ADR-0001/0002, `supabase/schema.sql` header, ClockOffsetStore, SyncClock.

- **Every write sets** `updated_at = max(now() + clockOffset, previous_updated_at + 1ms)` — offset-corrected toward server time AND strictly monotonic per row. No DB trigger overrides it.
- **`pending_sync = true`** on every local write (local-only outbox flag; never sent to / read from the server as authority).
- **Clock offset acquisition:** how the client learns `clockOffset` (server-time probe) — pin the mechanism so web computes the same corrected timestamps.
- **To pin at grill:** the exact monotonic-bump semantics, offset refresh cadence, and what web uses in place of Android's DataStore-backed `ClockOffsetStore`. Web writing naive `Date.now()` timestamps would corrupt LWW ordering for **both** clients — call this out as the #1 web pitfall.

---

## 3. FK push/pull order

*Status: ⬜ not frozen.* Source: ADR-0009 + the newer leaf tables.

Both push and pull process tables in FK order; upserts are idempotent by `id`; an interrupted sync just resumes.

- **Core order (ADR-0009):** `users → couples → accounts → categories → recurring_rules → transactions → budgets → notes → note_images`.
- **Partner variants** follow their owned counterparts.
- **Newer leaves to slot in:** `savings_goals` + `goal_contributions` (V1.5), `transaction_images` (V1.6.5), `partner_debts` + `partner_debt_payments`.
- **✅ Confirmed live:** `notifications` sits at the **very end** of the order (`SyncTable.NOTIFICATIONS` is last) — built 2026-07-27 (v1.7.1 Item 6, migration #22, Room v30). Own-user-only, no partner variant — see §1b above.
- **To pin at grill:** the single authoritative full ordering incl. every table currently in `SyncTable`/`schema.sql`, and the pull cursor rule (`server_rev > cursor`, per-table cursor).

---

## 4. Money representation & derived math

*Status: ⬜ not frozen.* Source: `supabase/schema.sql` (numeric columns), [IponSerializers.kt](../../app/src/main/java/com/iponlove/app/core/network/serializers/IponSerializers.kt), ADR-0007.

- **Canonical storage:** `numeric(14,2)` on every money column (exact decimal, 2 places).
- **Wire format:** JSON **double** (`BigDecimalSerializer` uses `PrimitiveKind.DOUBLE`). JS `Number` is also IEEE-754 double, so web is naturally consistent with the wire *for `numeric(14,2)` magnitudes* — but web MUST NOT do arithmetic in native floats.
- **Derived per-client (never stored):** account balance (opening + ledger, ADR-0007), analysis aggregations, budget spent/percent, debt splits, netting. Two clients that round differently show different numbers for identical rows.
- **To pin at grill:** the exact rounding mode + scale for every derived computation (default appears to be HALF_UP, scale 2 — confirm per site), division/percent rules, and a mandate that web use a decimal library (e.g. decimal.js), not `number`, for all money math. Provide worked examples (budget %, transfer split, netting) as conformance vectors.
- **New derived-math site to pin (added 2026-07-28, v1.7.1 Item 10 / ADR-0055):** the debt-overpay allocation — `DebtAllocationCalculator.allocate(orderedTickedDebts, lump)`, a pure function: fills same-direction "I owe" debts **in tick order**, each floored at its own remaining, the **last-ticked debt absorbs the remainder**, blocked (never silently capped) above the ticked-total ceiling. If web ever builds or even just *renders* this feature, the fill algorithm must match exactly — it determines which named debt shows as paid, not just a total.

---

## 5. Conflict resolution & partner data

*Status: ⬜ not frozen.* Source: ADR-0003/0004/0005/0011/0013, `supabase/schema.sql` views + policies.

- **Default conflict:** row-level LWW by `updated_at`.
- **Exception:** shared notes use **conflict copy**, not LWW (ADR-0003).
- **Partner data:** read only through **redacting views**, never base tables (ADR-0005). A partner row arriving flagged private/deleted/unshared means **purge** the local copy, not upsert.
- **Users row is a synced entity:** `EnsureCurrentUserRow` runs before any other write; no DB trigger creates it (ADR-0013). Pushes before accounts/categories.
- **Couple ops are RPC-only:** `create_couple`, `redeem_invite`, `rotate_invite_code`, `unpair` (ADR-0006/0008). Unpair triggers a bulk purge of all replicated non-owned rows.
- **To pin at grill:** the exact conflict-copy algorithm for notes; the precise purge triggers a web client must implement; the RPC signatures + expected error shapes.

---

## 6. Onboarding / starter-data seeding

*Status: ⬜ not frozen.* Source: [SeedStarterDataUseCase](../../app/src/main/java/com/iponlove/app/feature/onboarding/domain/usecase/SeedStarterDataUseCase.kt), MainActivity onboarding guard. See [W5](web-phase-0-prep.md#w5--starter-seeding-guard-parity).

- Seeding upserts on deterministic ids (§1) and overwrites in place — it does **not** itself guard tombstones.
- The re-seed guard is **upstream** and checks the **server** ("already onboarded?"), NOT local emptiness. Web (empty local by definition) MUST use the server check or it resurrects deleted starter rows.
- **To pin at grill:** the exact server-side "already onboarded?" predicate; tombstone-respect rule; whether seeding is skipped entirely on web (rely on the Android seed syncing down) or run with the guard.

---

## 7. Image / attachment pipeline

*Status: ⬜ not frozen.* Source: `note_images`/`transaction_images`/`couple-banners`, StorageAuthInterceptor, [CompressReceiptUseCase](../../app/src/main/java/com/iponlove/app/feature/transactions/domain/usecase/CompressReceiptUseCase.kt), ADR-0043. See [W6](web-phase-0-prep.md#w6--image-pipeline-parity-compression-private-buckets-storage-rls-orphans).

- Attachments store a `storage_url` (not a local path); files live in **private** buckets, fronted by authenticated URLs.
- Web must replicate compression + bucket/path naming, use authenticated access, and needs storage RLS/CORS that admits the web origin.
- **To pin at grill:** the exact bucket/path naming convention; compression target params; the authenticated-URL scheme; storage-policy changes needed for web; the orphan-cleanup contract.
- **Scope ruling (2026-08-03 grill):** everything above freezes from the existing Android sources **except the specific web origin** admitted by storage RLS/CORS, which cannot be pinned until hosting is chosen (Vercel vs. Cloudflare Pages, still open in [W8](web-phase-0-prep.md#w8--web-app-greenfield-foundational-design)). Record that one line as explicitly pending rather than inventing a value; it is the sole sanctioned `⬜` remaining once this section is filled.

---

## 8. Entitlement (read on web, write locked)

*Status: ⬜ not frozen.* Source: ADR-0044, `subscription-paywall-design.md`. See [W1](web-phase-0-prep.md#w1--lock-the-entitlement-columns-rls--validating-write-rpc) / [W7](web-phase-0-prep.md#w7--web-premium-purchase-path-play-cant-sell-on-web).

- Entitlement (`is_premium`/`premium_until`/`entitlement_source`) is **readable** on web via the synced `users` row.
- **Writes must go through the validating RPC (W1), never a direct column update** — the current self-writable RLS is the piracy vector web exposes.
- **To pin at grill:** the frozen RPC contract (once W1 is designed); how web reads entitlement offline; the web purchase → entitlement-write flow (W7).

---

## 9. Atomic multi-row writes — Android has a primitive web doesn't

*Status: ✅ **FROZEN 2026-08-03** (W2 grill) — grouped multi-row settlement stays Android-only; the forward-looking rule below is the binding part.* Source: v1.7.1 Item 10 / ADR-0055, `LocalTransactionRunner`; decision recorded in [ADR-0063](../adr/0063-web-v1-is-online-only.md).

Android's debt-overpay cascade (2026-07-28) became the app's **first true atomic multi-row write** — one EXPENSE transaction + N `DebtPayment` rows must commit all-or-nothing, since a partial write would leave the ledger overstating what reached the debts. It's implemented via `RoomDatabase.withTransaction`, wrapped behind a small `LocalTransactionRunner` seam (originally built for Reset-finances, ADR-0037; reused here with no new infra).

**This has no client-side equivalent on web.** `@supabase/supabase-js` issues independent REST calls per table — there is no client-side "wrap these N inserts in one transaction" primitive against PostgREST. If web ever needs the same all-or-nothing guarantee (this feature, or a future one shaped like it), the write must move **server-side**: a Postgres `SECURITY DEFINER` RPC function that performs the whole multi-row write in one SQL transaction, called once from the client. That's a different shape than the Android implementation (client-orchestrated vs. server-orchestrated) — so this is *not* just "port the Kotlin logic to TypeScript."

**✅ Decision (2026-08-03 grill): leave it Android-only. Do not build the RPC now.**

The question turned out to depend on an upstream one — whether web has any local database at all — which the same grill settled as **online-only** ([ADR-0063](../adr/0063-web-v1-is-online-only.md)). With no browser-local store, web cannot mirror Android's local-transaction approach, so authoring grouped settlements on web would require the `SECURITY DEFINER` RPC. That was judged not worth building yet:

- **The gap is convenience, not correctness.** ADR-0055's own analysis establishes that the money end-state is identical no matter how a lump is spread across same-direction debts — only *which labeled debt reads as paid* changes. A web user settling three debts one at a time reaches exactly the same financial position, losing the single grouped expense line and the tick-order choice, nothing more.
- **Android must not switch to the RPC either.** Android is offline-first; routing this write through a server function would require connectivity at settle time, which is a regression, not a unification. So "one implementation for both clients" is not actually on the table here — only "Android-only now" or "two implementations."
- **Web still reads grouped settlements correctly** — the rows are ordinary transactions and debt payments; only authoring is unavailable.

**Frozen forward-looking rule (the binding part):** any *future* feature requiring an all-or-nothing write across multiple tables **must be designed as a server-side `SECURITY DEFINER` RPC from the start if web needs to author it**. The Android `RoomDatabase.withTransaction` pattern does not port to a browser client, so "build it Android-style and port later" is never available for that class of feature — the port is a re-architecture, not a translation.

**Revisit trigger:** web needing to author grouped settlement (or any second all-or-nothing feature) is what justifies building the RPC; at that point expect two implementations (Android local transaction + server RPC) and budget for keeping them consistent.

- **Ties to:** [W8](web-phase-0-prep.md#w8--web-app-greenfield-foundational-design) (its offline-first tension, now resolved), [ADR-0063](../adr/0063-web-v1-is-online-only.md).

---

## Conformance checklist (fill as sections freeze)

- [ ] §1 Deterministic UUIDs — algorithm + namespace + all name strings + test vectors
- [ ] §2 LWW write rule — offset + monotonic bump + clock-offset acquisition
- [ ] §3 FK order — single authoritative full ordering + cursor rule
- [ ] §4 Money math — rounding/scale per derived site + decimal-lib mandate + vectors
- [ ] §5 Conflict resolution — note conflict-copy algo + partner purge triggers + RPC shapes
- [ ] §6 Seeding — server "already onboarded?" predicate + tombstone rule
- [ ] §7 Images — bucket/path convention + auth scheme + storage RLS/CORS *(allowed web origin deliberately pending the hosting choice — see the scope ruling in §7)*
- [ ] §8 Entitlement — validating RPC contract + offline read + web purchase flow
- [x] §9 Atomic multi-row writes — ✅ **FROZEN 2026-08-03**: Android-only, no RPC now, plus the forward rule that future all-or-nothing features are server-side from the start ([ADR-0063](../adr/0063-web-v1-is-online-only.md))
- [x] §1b Notification-inbox composite ids — ✅ already confirmed live (schema + build, no grill needed, just adopt as-is)
