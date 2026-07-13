# Premium entitlement is a client-trusted advisory column on the synced users row, never server-enforced

## Context

Grill of `docs/build/subscription-paywall-design.md` (2026-07-08) resolved the mechanism under D2/D4 — decisions that named `is_premium`/`premium_until`/`entitlement_source` on the `users` row and "client-side only, RTDN deferred" but skipped *how* a Play purchase becomes a synced column, *who wins* when they diverge, and *what entitlement is forbidden from touching*. Four properties had to be pinned before the dormant infra could be built, because they cut across sync (ADR-0002/0009), the redacting partner views (ADR-0005), unpair (ADR-0008/0018), and the users row as a synced entity (ADR-0013).

The forces:
- The **source of truth for a one-time purchase is Play Billing** (`queryPurchasesAsync` → `OWNED`) — device-local, tied to the Google Play account.
- The **couple must read each other's status** (D1 either-partner-unlocks-both) — which requires entitlement to travel through the existing partner-replication fabric, i.e. live on the synced `users` row (Supabase-login-bound), not in device-local DataStore.
- Play-account identity and Supabase-login identity can differ, and the two representations (Play state vs the column) can drift (refund, reinstall, beta comp with no purchase).
- **No gated item has server cost** (every gate is cosmetic, a `Cap count`, or ad-removal — and ads are dropped; AI is explicitly out, D8), so paying for a server-side verification pipeline now buys nothing.

## Decision

**Entitlement is a client-maintained cache of Play state, carried on the synced `users` row, trusted client-side with no server verification. It is a pure advisory read-layer over the sync fabric — it never gates sync, replication, or visibility, and caps are enforced best-effort at create-time only.**

Concretely:

1. **Column-as-cache, reconciled on foreground.** The billing client reconciles `is_premium`/`premium_until` from `queryPurchasesAsync` on every foreground (the existing sync trigger). It is a normal dirty/`pending_sync` write with offset-corrected `updated_at`. `entitlement_checked_at` records the last successful reconcile (diagnostic only, never read by a gate).

2. **Asymmetric trust.** For *your own* entitlement, **Play Billing is authoritative** — the column only ever mirrors it. For your *partner's*, the **column is authoritative and trusted unconditionally**, because a device cannot query the other Play account. This asymmetry is the reason entitlement lives on the synced row at all. The redacting partner view exposes `is_premium`/`premium_until` (not redacted) so the couple can resolve `Effective access`.

3. **No server enforcement; client-side spoof accepted as bounded.** There is no RTDN and no Postgres-side check. A rooted client can self-assert `is_premium = true`, and because shared surfaces unlock on `me.active || partner.active`, one partner's spoof unlocks the couple's joint features for both. Accepted because every gated item has zero server cost. **Scheduled prerequisite:** the first server-cost feature (AI companion, Horizon #3, D8) MUST add Play RTDN → server-side purchase verification *before* it can gate on entitlement. This is a planned addition, not hypothetical — Alvin intends to ship AI.

4. **`entitlement_source ∈ {PLAY, GRANT, NONE}` guards beta comps.** A `Premium grant` (beta comp, D3/§10.8) is written server-side straight to the row (`is_premium=true`, `premium_until=null` or a beta-end date, `entitlement_source=GRANT`) so it propagates to the partner like a purchase. The foreground reconcile loop **skips `GRANT` rows** — the single case where the device does *not* defer to Play — so a comp is never wiped by `queryPurchasesAsync` returning `NOT_OWNED`. Revoke = flip the row to `NONE`; normal reconciliation resumes.

5. **Advisory-only, best-effort.** Entitlement never changes what replicates or what is visible; `Freeze` means over-cap rows stay visible + read-only, blocked only on *new* creation. Because there is no server rejection, concurrent cross-device creates can transiently exceed a cap — tolerated. `Effective access` resolves scope from the **ownership of the specific row being gated** (couple-owned ⇒ shared ⇒ `me.active || partner.active`; user-owned or entity-less ⇒ individual ⇒ `me.active`), not from a per-feature constant.

6. **Cold-start is fail-open.** A device that does not yet know `Enforcement` is ON (fresh offline install, no sync) treats it as OFF and stays fully unlocked, self-healing on first foreground sync — so a paying customer reinstalling offline is never wrongly locked. This supersedes the design doc's earlier fail-closed cold-start framing.

## Consequences

- **Entitlement joins the sync fabric.** `is_premium`/`premium_until`/`entitlement_source`/`entitlement_checked_at` are columns on `users` (already first in the ADR-0009 FK order, so entitlement lands before anything that gates on it) and are added to the redacting partner-users view projection. Device-local DataStore is *not* the store of record (D2).
- **Unpair needs no entitlement-specific rule.** A partner who loses partner-derived premium and lands over a free cap (via `Revert-to-creator`, ADR-0018) resolves as ordinary `Freeze` — read-only, block-on-create, nothing deleted.
- **The entitlement state machine collapses** to `OWNED`/`NOT_OWNED` under one-time billing (D7); the grace/lapse/offline-stale states are dormant, retained only via the nullable-`premium_until` code path for a possible future subscription re-pivot.
- **Reconcile must respect `entitlement_source`** — a build that naively writes `is_premium` from Play state on every foreground would flip-flop and wipe every beta comp each session. This is the non-obvious trap the ADR exists to prevent.
- **The spoof surface is documented and bounded**, with a hard gate on the AI feature: no server-cost feature ships against this trust model until server verification lands.

## Suggested build

Opus for the `core/entitlement` state/reconcile/`Effective access` logic (cross-ADR: sync, redacting views, unpair, couples governance) — JVM-testable behind a billing interface per the Testing Policy; Sonnet for mechanical gate placement once the pattern is locked.

## As-built addendum (2026-07-13, flip re-grill)

The Phase 1–2 build (S1–S10) implemented this ADR with four refinements the text above predates — all behavior-compatible, recorded here so the ADR matches the code:

1. **`cap_overrides` is tier-scoped** — `{"free":{…},"premium":{…}}` (S2), so a free-tier tuning can never bleed onto premium limits.
2. **Boolean gates read a reactive seam** — `PremiumGate.observeLocked(scope)` (S9), not per-call checks; count caps use create-time `checkCap` (S7/S8); concrete ceilings use `observeLimit(scope, limitOf)` (S10).
3. **Revert-on-lapse (G8) is a pure read-time derivation, not a write** — `ThemePalette.effective(locked)` downgrades at render; the chosen palette is never mutated in DataStore, so re-unlock (or flipping enforcement back OFF) auto-restores with nothing to un-write. Same pattern for the note ceiling: `NoteCharLimit.effectiveLimit(tierLimit, seededLength)` freezes an over-cap note at its seeded length rather than truncating (S10 sub-gate 1).
4. **Enforcement-OFF is therefore a zero-residue rollback lever** — a consequence of (3): no lock ever writes state, so the flip-day abort plan is just the kill-switch (§10.7b row A2).
