# Entitlement columns are write-locked at the database, reachable only through one RPC

## Context

[ADR-0044](0044-entitlement-client-trusted-advisory-column.md) made entitlement a **client-trusted advisory column** on the synced `users` row, and was explicit about the accepted risk: *"A rooted client can self-assert `is_premium = true`, and because shared surfaces unlock on `me.active || partner.active`, one partner's spoof unlocks the couple's joint features for both."* That was accepted on a specific, stated condition — **every gated item has zero server cost** — with a named prerequisite that the first server-cost feature (AI, Horizon #3) must add Play RTDN + server verification before gating on entitlement.

The web-app track ([W1](../web/web-phase-0-prep.md#w1--lock-the-entitlement-columns-rls--validating-write-rpc), 2026-07-26) surfaced that the *other* half of ADR-0044's risk assessment — not the cost side, the **attacker-effort** side — is the part that breaks first, and it breaks on a different trigger than AI:

- `users_update` is `using (id = auth.uid()) with check (id = auth.uid())` ([schema.sql:499](../../supabase/schema.sql#L499)) — row-scoped, with **no column restriction**. A user may write every column on their own row.
- On Android that requires rooting the device or MITM'ing TLS. On web it requires **opening dev tools** and running `supabase.from('users').update({ is_premium: true })`. The forged row then syncs *back down to the phone* through our own sync engine, and couple governance (`me.active || partner.active`) unlocks the partner too.

So the trust model doesn't survive contact with a browser, and it degrades on the web timeline (Q4 2026), not the AI timeline. Enforcement is still dormant (kill-switch OFF, everything unlocked), so nothing is exploitable *today* — which is what makes this the right moment to fix the shape cheaply, before either trigger arrives.

## Decision

**The four entitlement columns are stripped of direct write access at the Postgres privilege layer, and become reachable only through a single `SECURITY DEFINER` RPC. That RPC is a passthrough today — it validates nothing about the purchase — so this closes the *shape* of the hole, not yet the hole itself.**

### 1. A column *allowlist*, not a trigger — and not a column-level `REVOKE` either

The privilege layer is the right mechanism, but the obvious spelling of it does not work.

**Corrected during the build (2026-07-29).** This ADR originally specified `REVOKE UPDATE (is_premium, premium_until, entitlement_source, entitlement_checked_at) ON users FROM authenticated`. That statement is a **silent no-op on this database**: Supabase grants `authenticated` table-level `UPDATE` on `public.users`, and Postgres lets a table-level privilege win over a narrower column-level revoke — no error, no warning, the columns stay writable. Verified empirically before shipping (temp table, table grant + column revoke → `has_column_privilege(...) = true`). Had it shipped as written, every verification short of actually attempting a forged write would have reported success.

The form that works drops the table grant and hands the writable columns back:

```sql
revoke update on users from authenticated;
grant update (id, display_name, avatar_url, accent_color, avatar_motif,
              couple_id, created_at, updated_at, server_rev) on users to authenticated;
```

Postgres then rejects any `UPDATE` naming a column outside that list, independently of (and underneath) the row-level `users_update` policy, which is left exactly as-is. A `SECURITY DEFINER` function runs as its owner and is unaffected by the grant, which is what makes it the sole remaining door.

**This inverts the model from denylist to allowlist, which has a standing cost:** every column added to `users` from now on is unwritable by the client until it is added to that grant, and the failure mode is a field that silently stops syncing rather than a loud error. Adding a `users` column is now a two-step change. The upside is that the default is safe — a future entitlement-shaped column is locked the moment it exists, rather than needing someone to remember to lock it.

`server_rev` and `created_at` are in the allowlist because the client *names* them in its upsert payload, even though `trg_rev_users` overwrites `server_rev` server-side regardless; the privilege check keys on the column being named, not on whether the value survives.

`anon` keeps its table-level grant deliberately — `users_update` is `using (id = auth.uid())` and `auth.uid()` is null for `anon`, so RLS already refuses every anonymous write. Narrowing it would be scope creep on a hole that is already closed.

A `BEFORE UPDATE` trigger comparing old/new values was the alternative. **Rejected:** it needs a side-channel to distinguish "the RPC did this" from "a client snuck it in" (typically a `set_config` session flag), which is a hand-built mechanism that can itself fail open — whereas a revoked privilege is a flat, declarative fact with no logic to get wrong. The one thing a trigger buys that a revoke cannot is **conditional** self-writes (e.g. permitting self-*downgrade* but not self-upgrade); no such case exists here.

All four columns are locked as one group, including the diagnostic `entitlement_checked_at`, so there is no exception to remember. This costs nothing because of a property of the existing reconcile: [`EntitlementRepositoryImpl.reconcile()`](../../app/src/main/java/com/iponlove/app/core/entitlement/EntitlementRepositoryImpl.kt#L59) writes **only when the Play-derived state actually differs**, so `entitlement_checked_at` already advances only on a real change, not every foreground.

### 2. The client must stop shipping entitlement in the ordinary profile upsert

This is the non-obvious consequence, and the reason this item is a client change and not a migration.

The `users` push is a **full-row upsert**: [`SupabaseUserRemoteSource.push()`](../../app/src/main/java/com/iponlove/app/feature/user/data/remote/SupabaseUserRemoteSource.kt#L16) sends `UserDto`s built from the whole local entity ([`UserMapper.toDto()`](../../app/src/main/java/com/iponlove/app/feature/user/data/UserMapper.kt#L30)). A dirty row caused by changing *accent colour alone* still carries `is_premium` / `premium_until` / `entitlement_source` in the payload — unchanged, but **present**.

Column-level revoke keys on a column being *named* in the statement, not on its value changing. So the naive version of this ADR — revoke, ship, done — would break **every ordinary profile edit in the app**, not just spoof attempts. The general push path must therefore omit the entitlement columns, and `writeSelfEntitlement()` must route through the RPC instead of riding the generic dirty-row push.

### 3. Offline-first is preserved: local write stays, the RPC call is what retries

Entitlement writes today are ordinary offline-first writes — local Room write, `pending_sync = true`, retried by the sync engine until it lands. Swapping in a direct RPC call would make entitlement the one write in the app that *fails* when offline.

The local write and the `pending_sync` flag stay exactly as they are; only the **push target** changes (RPC instead of generic upsert), so an offline reconcile still applies locally at once and uploads on the next successful sync. This deliberately preserves [ADR-0044 §6](0044-entitlement-client-trusted-advisory-column.md)'s fail-open cold-start — the tightening must not re-lock a paying customer who reinstalls on a plane.

### 4. The `GRANT` beta-comp rule moves from client convention to server rule

The rule that a [[Premium grant]] must never be overwritten by a Play reconcile currently exists *only* as a client-side early return ([`EntitlementRepositoryImpl.kt:37`](../../app/src/main/java/com/iponlove/app/core/entitlement/EntitlementRepositoryImpl.kt#L37)). The RPC enforces it too: it refuses to downgrade a row whose `entitlement_source = 'GRANT'`.

This is business logic in the database, which the house style is otherwise sparing about. It earns its place because the *second* client (web) will have its own separate implementation of the reconcile loop, and a comp silently wiped on a tester's account is both easy to cause and annoying to diagnose. Two lines of SQL beats re-deriving the convention per platform.

Beta comps themselves stay **manual SQL by the database owner**, which is unaffected by a revoke aimed at `authenticated`. A `CHECK` constraint restricting `entitlement_source` to `PLAY | GRANT | NONE` ships alongside — today that's only a comment ([schema.sql:74](../../supabase/schema.sql#L74)), and a hand-typed grant is exactly the write that would introduce a typo.

### 5. Real receipt validation is deliberately **not** in this change

The RPC accepts the client's self-reported Play state and writes it. It does not call Google's API, and there is no Supabase edge function.

Deferred on purpose:

- **Nothing is exploitable yet.** Enforcement is dormant; a forged `is_premium` unlocks nothing that isn't already unlocked for everyone.
- **The two triggers are both future-dated** — enforcement flip-day, and web shipping (Q4 2026).
- **The infrastructure is shared with something else already committed.** AI credits must be server-metered via an edge function ([`subscription-paywall-design.md` D8](../build/subscription-paywall-design.md)); whichever of the two lands first pays the setup cost and the second is a small marginal add. Guessing at the shape now, months early, risks building the wrong one twice.

**Stated plainly, because it would otherwise read as solved:** after this change a forged call to the RPC still sets `is_premium = true` with no verification. The bar rises from "write any column on your row" to "know the RPC exists and call it correctly" — a real but modest improvement. **This ADR closes the door; it does not yet lock it.** The lock is receipt validation, which must land before enforcement is flipped ON or the web client ships a purchase path, whichever comes first.

## Consequences

- **ADR-0044's trust model is amended, not overturned.** Entitlement is still a client-maintained cache, still advisory-only, still fail-open, still never gates sync or visibility. What changes is that the *write* is funnelled through one auditable path instead of being a raw column write — the necessary precondition for verification, landed early and cheaply. See ADR-0044's amendment note.
- **ADR-0044's named prerequisite gains a second trigger.** It said server verification must precede *AI*. It must now equally precede **enforcement flip-day** and **the web purchase path** ([W7](../web/web-phase-0-prep.md#w7--web-premium-purchase-path-play-cant-sell-on-web)) — three triggers on one dependency.
- **Adding a column to `users` is now a two-step change** (add the column, add it to the `grant update (...)` allowlist), and skipping the second step makes that field silently stop syncing rather than fail loudly. This is the price of the allowlist form being the only one Postgres actually honours here — see decision 1. No other table is affected.
- **`users` becomes the first table whose push is not a plain full-row upsert.** A per-table exception in the sync engine is a mild wrinkle in an otherwise uniform mechanism; it is confined to the push payload and does not touch the pull path, cursor, LWW rule, or the ADR-0009 FK order.
- **Reads are entirely untouched** — own and partner entitlement still arrive through the ordinary pull and the redacting partner view. Only writes are constrained.
- **Existing data needs no migration.** Revoking a privilege does not alter stored values; current `is_premium = true` rows (beta comps) keep working.
- **The web client inherits the constraint for free.** Whatever the web app is written in, it hits the same Postgres privilege wall, so it cannot re-open this hole by accident — including a Kotlin Multiplatform `shared` module ([W10](../web/web-phase-0-prep.md#w10--extract-domaindatasync-layer-into-a-kotlin-multiplatform-shared-module)), where the RPC call would live in `commonMain` and be shared rather than reimplemented.

## Suggested build

Opus/high — the change spans the paywall trust model (ADR-0044), Postgres privileges + RLS, and the sync engine's push path for a table other tables' ordering depends on (ADR-0009/0013). Tier-1 unit tests are required per the Testing Policy (entitlement + sync logic): that the general `users` push omits the four columns, that `writeSelfEntitlement` routes to the RPC, that an offline entitlement write still applies locally and retries, and that a `GRANT` row survives a `NOT_OWNED` reconcile — the last one now asserted on both sides of the boundary.
