# Love, Ipon — Web App Build Progress

**This is the cold-start orientation file for the web-app track** (Post-V1 Horizon #6, target **Q4 2026**). It's the web-side sibling of [`docs/build/project-build-progress.md`](../build/project-build-progress.md) — read this first when working on anything web, then the phase ledger for whatever's in flight, then [`cross-platform-contract.md`](cross-platform-contract.md) for the rules the web client must not violate.

**Scope of this track:** the web app is a *separate, greenfield client* over the *same* Supabase backend and the *same* sync contract as the Android app. Two kinds of work live here:
1. **Base-app / backend changes** that must land in the **Android repo + Supabase** so a second client is safe (security, server-side jobs, synced-vs-local preference decisions). These execute as native slices but are tracked here because the web app is *why* they're needed.
2. **Web-app greenfield design + build** — stack, hosting, auth, the sync-engine port. Not yet designed/grilled.

**Same workflow as native:** book each change as a numbered item in the in-flight phase doc **before** building/deciding it; grill anything novel; lock resolved designs in an ADR (shared `docs/adr/` numbering) or in [`cross-platform-contract.md`](cross-platform-contract.md). Update this file's "Current state" after each committed change.

---

## Current state (as of 2026-07-28, later)

**Track opened 2026-07-26** out of a "what must change in the base app before the web app?" audit (see the Origin note below). Phase 0 (design + base-app prep) is the active phase: [`web-phase-0-prep.md`](web-phase-0-prep.md), **10 items booked (W1–W10)**, all awaiting a formal grill except W8/W10 whose *direction* is now locked (below).

**Architecture direction LOCKED 2026-07-28 (conversation, not a formal `/grill` — W8 still needs one to stress-test edges): the web app shares its domain/data/sync layer with Android via Kotlin Multiplatform (Kotlin/JS target), with a native React/Tailwind UI on top — not a from-scratch TypeScript reimplementation of the sync/money/dedup logic.** This is the single most consequential decision in the track so far — it changes what several other items mean:
- **W2's role shrinks.** The cross-platform contract doc still documents the invariants, but for whatever moves into the shared module, "matches exactly" stops being a spec a second team must hand-follow and becomes a fact of the code (one implementation, two compile targets). It stays load-bearing for what genuinely can't be shared (image compression, atomic writes) and for the period before the extraction happens.
- **W8's stack proposal was rewritten** — the web repo now owns UI + wiring only (Next.js/React/Tailwind/shadcn, unchanged from the earlier draft); all domain/sync/money logic is consumed from the shared module instead of reimplemented in TypeScript. The earlier advice to use `decimal.js`/a `uuid` npm package/Dexie-ported sync is superseded (kept in the doc only as a record of what not to do).
- **New W10** books the actual extraction work — explicitly **not scheduled now**. No mobile code needs to change today; the domain layer's existing "zero Android imports" discipline (a house rule already, originally for JVM-testability) already produces the shape this needs. The only "prep" is holding that line on new native features, which isn't a new ask.
- **Partly driven by a longer-horizon consideration, not just Q4 web:** Alvin expects to afford the $99/yr Apple Developer fee in a couple of years — the same shared module extends to an `iosMain` target later with no redo, if `commonMain` is built with multiplatform-safe types (`kotlinx-datetime`, `kotlin-bignum`) and clean `expect`/`actual` boundaries from the start. No iOS-specific work needed yet.

**Do first regardless of the above (highest leverage, both need a grill):**
- **W1 — lock the entitlement columns.** On Android the self-writable `is_premium` column is an accepted advisory risk (ADR-0044); on web (or any second client) it becomes one-line piracy from dev tools. Backend/schema change, should land regardless of web timing or tech stack — orthogonal to the KMP decision. Opus/high when grilled (cross-ADR: 0044 + couple governance + a new validating RPC).
- **W2 — freeze the cross-platform contract** for whatever doesn't move into the shared module (see above).

**Notification-submodule fallout (still current, from the native v1.7.1 batch landing since the track opened):**
- **The notification inbox is now BUILT and live** (v1.7.1 Item 6, [ADR-0053](../adr/0053-notification-inbox-synced-source-of-truth.md), Room v30, Supabase migration #22) — deliberately built **synced now**, with the ADR's own stated reason being that the web app is imminent. [`cross-platform-contract.md` §1b](cross-platform-contract.md#1b-notification-inbox-composite-ids--a-second-distinct-scheme--built--live-v171-item-6--adr-0053) records its id scheme.
- **ADR-0053 itself names a deferred question** — server-side notification generation — booked as **W9**.
- **The debt-overpay cascade** (v1.7.1 Item 10, ADR-0055) is the app's first atomic multi-row write, with no client-side web equivalent (`@supabase/supabase-js` has no transaction primitive) — this stays true regardless of the KMP decision, since Room's transaction API doesn't cross to web either way. Tracked in [`cross-platform-contract.md` §9](cross-platform-contract.md#9-atomic-multi-row-writes--android-has-a-primitive-web-doesnt).

Everything else in Phase 0 (W1, W3–W7, W9) is booked with the design tension captured; none is build-ready yet.

---

## Origin (2026-07-26)

Alvin asked, ahead of the Q4 web app, what must change in the Android base app — specifically whether any *local-only* data should move server-side. A code audit found the entity data model is already web-ready (offline-first + Supabase sync was built for exactly this), but surfaced risks that stay invisible while there's only one client:

- **The premium/entitlement columns are self-writable via RLS** (`users_update` allows a user to write any column on their own row, incl. `is_premium`/`premium_until`/`entitlement_source`). Accepted for Android (needs root/MITM); trivial piracy on web. → **W1**
- **Client-side determinism is load-bearing.** `DeterministicUuid.v5` (namespace `9d8f6c2e-…`) collapses would-be duplicates across devices for recurring materialization, debt netting, paid-on-behalf, transfer-fee category, and starter seeding. The web client must reproduce the exact namespace + every name string or it *creates* duplicates instead of merging. → **W2**
- **Money is `numeric(14,2)` server-side but derived client-side**, and the wire format is `double` (`BigDecimalSerializer` → `PrimitiveKind.DOUBLE`). Balances/analysis/budget %/splits are computed per-client, so web must replicate the exact scale + rounding or two clients show different numbers for identical rows. → **W2**
- **Client-only compute has no server counterpart** — recurring materialization ([RecurringScheduler](../../app/src/main/java/com/iponlove/app/feature/recurring/domain/usecase/RecurringScheduler.kt)) and budget-alert evaluation ([BudgetAlertWorker](../../app/src/main/java/com/iponlove/app/feature/budgets/worker/BudgetAlertWorker.kt)) run only on the Android device. Supabase has no pg_cron / edge functions today (only `server_rev` triggers + RLS + couple RPCs). → **W3**
- **Starter seeding** upserts on deterministic ids and does *not* itself guard tombstones; the "already onboarded?" guard lives upstream and checks the **server**, not local emptiness. Web has empty local state by definition. → **W5**
- **Local-only DataStore preferences** (theme palette, navbar layout, privacy default, notification prefs/thresholds/mutes, tutorial progress) don't follow the user to web. Some are correctly per-device (app-lock PIN, sync cursor, clock offset); others are a product call. → **W4**
- **Images are already URL-based (web-ready)** in private Storage buckets, but web must replicate client-side compression, authenticated-URL access, storage-RLS origin admission, and the orphan-cleanup story. → **W6**
- **Play billing can't sell on web** — entitlement column is readable on web, but the purchase path is Android-only. → **W7** (ties to W1)

Full discussion is preserved in [`web-phase-0-prep.md`](web-phase-0-prep.md) per item.

---

## Phase index

| Phase | What | Doc |
|---|---|---|
| Phase 0 | Design + base-app/backend prep (W1–W10) — make a second client safe; design the web app itself. **Active; W8/W10's architecture direction locked, formal grill still pending.** | [web-phase-0-prep.md](web-phase-0-prep.md) |

New phases get their own doc and a row here once Phase 0's shape is known.

---

## Standing design docs

- [`cross-platform-contract.md`](cross-platform-contract.md) — the frozen rules the web client must reproduce (determinism, sync protocol, money math, seeding, images). **Skeleton — to be filled/frozen by grilling W2.**

---

## Relationship to the native track

- **Shared:** the Supabase schema (`supabase/schema.sql`), the `docs/adr/` numbering, all sync semantics (ADR-0001/0002/0003/0005/0009/0012), the entitlement model (ADR-0044), the notification inbox (ADR-0053 — already built *synced* partly *because* the web app is imminent).
- **Native repo owns:** the Android client, Room, WorkManager jobs, DataStore. Any "base-app change" item here (W1, W3, W4, W5, W6) executes as a native slice and is *also* booked in the in-flight native version doc when built — this track just records the web-driven *why* and keeps them from being forgotten.
- **This track owns:** the web codebase (greenfield, likely a separate repo once it exists) and the contract that binds the two clients.
