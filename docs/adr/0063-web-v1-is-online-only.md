# Web v1 is online-only; the phone stays the offline surface

## Context

Grilled 2026-08-03 as part of [W2](../web/web-phase-0-prep.md#w2--freeze-the-cross-platform-contract-determinism--sync-protocol--money-math)'s contract freeze ([v1.7.4.md Item 1](../build/v1.7.4.md#item-1--freeze-the-cross-platform-contract-from-web-phase-0-w2)). [W8](../web/web-phase-0-prep.md#w8--web-app-greenfield-foundational-design) had listed "offline-first vs. online-only-first for web v1" as an open tension, and the 2026-07-28 Kotlin Multiplatform decision explicitly did not resolve it — sharing the sync logic means whichever model is chosen, the engine is reused rather than reimplemented.

The question surfaced from below rather than above. Contract [§9](../web/cross-platform-contract.md#9-atomic-multi-row-writes--android-has-a-primitive-web-doesnt) asks whether web should be able to author the debt-overpay cascade — one lump EXPENSE plus N `DebtPayment` rows that must commit all-or-nothing ([ADR-0055](0055-debt-overpay-cascade-guided-allocation.md) decision 7, the app's first true atomic multi-row write). Android guarantees this with `RoomDatabase.withTransaction`. `@supabase/supabase-js` issues independent REST calls per table and has no client-side transaction primitive against PostgREST — so §9's answer depends entirely on whether web has a local database at all. IndexedDB *does* support transactions, so an offline-first web client could mirror the Android approach; an online-only one cannot, and would need a `SECURITY DEFINER` RPC instead. §9 could not be answered without settling this first.

The app is Android-first for the Philippine market and offline-first by design — CLAUDE.md's standing convention is "Room is always read first; Supabase is background sync only", with the cursor and dirty-flag protocol in [ADR-0002](0002-sync-cursor-model.md). The web app is the near-term cross-platform target, expected to begin immediately after the receipt-OCR work.

## Decision

1. **Web v1 is online-only. It requires a connection and has no browser-local persistence layer.** It reads and writes Supabase directly through the shared repositories. **Rejected:** offline-first web v1 — building browser-local storage (IndexedDB) plus a sync layer is one of the largest single chunks in the entire web project, and it buys a use case that barely exists. The phone is the on-the-go surface where connectivity actually drops (logging a jeepney fare, a market run); the browser is the desk surface for review, planning, and bulk edits, where a connection is the norm.

2. **The phone remains the offline surface, and that asymmetry is intentional, not a gap to close.** The two clients have different jobs. Android keeps Room as its offline source of truth and its full push/pull engine; web is a thin online client over the same backend. Documenting this as a deliberate split prevents a future reader from reading it as an unfinished port.

3. **This is not a one-way door.** The domain/data/sync logic moves into the shared KMP module regardless ([W10](../web/web-phase-0-prep.md#w10--extract-domaindatasync-layer-into-a-kotlin-multiplatform-shared-module)), so adding web offline support later is **additive** — a local persistence implementation behind the already-shared repository interfaces — not a rewrite. No decision here forecloses it.

4. **Consequently, contract §9 stays Android-only: web will not author grouped multi-debt settlements in v1, and the `SECURITY DEFINER` RPC is not built now.** The gap is convenience, not correctness — ADR-0055's own analysis establishes that the money end-state is identical no matter how a lump is spread across same-direction debts; only *which labeled debt reads as paid* changes. A web user settling debts one at a time reaches the same financial position, losing the single grouped expense line and the tick-order choice. Web still *reads* grouped settlements correctly; only authoring is unavailable. **Rejected:** building the RPC now for day-one parity — real work to buy back a convenience.

5. **Android must not be migrated onto such an RPC either.** Android settles offline; routing that write through a server function would require connectivity at settle time, which is a regression rather than a unification. "One implementation shared by both clients" is therefore not available for this class of write — the real options are Android-only, or two implementations kept consistent.

6. **Frozen forward-looking rule: any future feature requiring an all-or-nothing write across multiple tables must be designed as a server-side `SECURITY DEFINER` RPC from the start if web needs to author it.** The `RoomDatabase.withTransaction` pattern does not port to a browser client, so "build it Android-style and port it later" is never available here — that port is a re-architecture, not a translation. This rule is the binding output of §9, more so than the Android-only ruling itself.

## Consequences

- **W8's sync-engine tension is closed**; hosting and web auth flows remain open. The web build can proceed on its foundational design without re-litigating the offline question.
- **Web has no local persistence layer**, so W8's "web's local persistence (if it goes offline-first)" caveat is resolved. `TanStack Query` over the shared repositories covers server state; there is no browser-side push/pull engine, no local outbox, and no `pending_sync` equivalent on web.
- **A visible feature asymmetry ships**: the multi-debt lump settlement exists on phone and not on web. This is accepted and should be presented as a platform difference rather than a missing feature.
- **Revisit trigger:** web needing to author grouped settlement — or any second all-or-nothing feature — is what justifies building the RPC. At that point expect two implementations (Android local transaction plus server RPC) and budget for keeping them consistent.
- **Offline web remains available later** as an additive layer behind the shared repository interfaces, per decision 3.
- **Rejected alternatives:** offline-first web v1 (decision 1); building the atomic-write RPC now (decision 4); migrating Android onto a server-side RPC for parity (decision 5).
