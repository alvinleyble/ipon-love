# Love, Ipon — Build Progress

**This is the cold-start orientation file** (see `CLAUDE.md`'s "How to Start Each Conversation"). Read this file first, then the version doc for whatever's in flight, then one reference feature folder and the relevant `supabase/schema.sql` table before writing code.

**Update this file's "Current state" section (and the relevant `vX.Y.md` file) after every commit.** This is the single source of truth for orientation in the next conversation — keep it current, not the git log.

---

## Current state (as of 2026-07-06)

**V1 through V1.6.3 are committed on `main`** (latest code: `abf9ffd` navbar editor root-route fix). Per-item detail for every shipped batch lives in the version docs indexed below — this section tracks only what's *in flight*.

**Nothing in flight.** [V1.6.3](v1.6.3.md) — beta feedback round 3 — is complete: all 9 buildable items committed, Item 3 deferred to Post-V1 Horizon #14 (see its row in the version table below for the full item list + hashes).

| Version | What | Doc |
|---|---|---|
| V1 | Core budget tracker + couples (accounts, categories, transactions, budgets, analysis, recurring, notes, auth, sync, pairing, combined view, shared budget, partner debt tracker, themes, widget) | [v1.md](v1.md) |
| V1.1 | Post-ship fixes (reinstall clobber, pairing color picker, rebrand) | [v1.1.md](v1.1.md) |
| V1.2 | 6 bug fixes incl. the live-sync (ADR-0015) rework | [v1.2.md](v1.2.md) |
| V1.3 | 14-item feature batch (customizable navbar, shared accounts/categories, receipts, debt netting/settlement, custom icons, tabbed analysis, recurring calendar) | [v1.3.md](v1.3.md) |
| V1.4 | Beta UX overhaul (8 slices — data-leak fix, nav declutter, module merges, beta feedback screen) | [v1.4.md](v1.4.md) |
| V1.5 | Beta Feedback Round 1 (12 slices — app-lock overlay, onboarding, 3-pin navbar, shared savings goals) | [v1.5.md](v1.5.md) |
| V1.5 post-ship audit | 11-item cross-cutting bug/edge-case audit, all closed | [v1.5-post-ship-audit.md](v1.5-post-ship-audit.md) |
| V1.6 | Prod-flavor redeploy fix (beta feedback visibility, version bump, savings-goal backfill) | [v1.6.md](v1.6.md) |
| V1.6.1 | Original 12-item batch feature-complete; 7 more items added 2026-07-05. Items 14/13/16 `DONE f4ae4c8` (nested nav graphs, Notes-as-module, Privacy Policy link); Items 15/18/19 `TODO` (tutorial, recurring pause/skip, budget rollover — ADR-0034/0035/0036); Item 17 (search) `DROPPED` | [v1.6.1.md](v1.6.1.md) |
| V1.6.2 | New batch, started 2026-07-05. Item 1: "restart fresh" data reset — locked (ADR-0037), **MOVED to Post-V1 Horizon #13**; Item 2: new-transaction screen persists over other tabs (bug) — `DONE 54d9359` (ADR-0039); Item 3: expand 3-step onboarding into a real per-module walkthrough — `DONE 7c85f05` (ADR-0038), verified on-device by Alvin; Item 4: recurring rule editor width — `DONE 642e070`; Item 5: budget rollover — `DONE 50d9a92` (ADR-0036); Item 6: duplicate-budget-to-next-month — `DONE 50d9a92` | [v1.6.2.md](v1.6.2.md) |
| V1.6.3 | Beta feedback batch (round 3), booked + grilled 2026-07-05, **complete 2026-07-06**. (8) truncated "shared" tag `DONE 35c4e2b`; (9) Notes heart→`SharedBadge` `DONE 6c5ec4f`; (7) uncategorised debt txns → "Debt settlement" label `DONE ae64ceb` ADR-0042; (1) Notes pin, synced `is_pinned` `DONE 70835c9` ADR-0040; (2) rollover reset acts on target month `DONE 29cc402` ADR-0041; (10) currency-symbol Horizon #14 doc/screen sync `DONE edc8249`; (4) "paid for partner" not syncing — staging schema drift, migration-only fix `DONE dfc2929`; (5) Goal editor draft lost on background <30s, `SavedStateHandle` mirroring `DONE fea0cff`; (6) Edit Navbar blocked tab switching, promoted to root-level route `DONE abf9ffd`. (3) multi-currency **deferred to Horizon #14** (display-symbol-only) | [v1.6.3.md](v1.6.3.md) |

**Post-V1 Horizon** — the only work beyond the numbered versions above, unscheduled by design (could land before or after production launch depending on priority/capacity). Reconciled 2026-07-04 against `PRD.md`/`ARCHITECTURE.md` (which had drifted out of sync with each other and with this list — see `v1.6.1.md` Item 5):

1. **Google Sign-In** — Supabase OAuth + Android Credential Manager. **Target: Q3 2026.**
2. **Facebook Login** — Supabase OAuth + Facebook SDK. **Target: Q3 2026.**
3. **AI companion** — hybrid, not pure BYOK: a capped, app-funded free tier (cheap model, e.g. Haiku, small monthly message allowance) for the mass-market sub-features (smart insights, NL transaction entry), with BYOK (user's own Anthropic key, encrypted on-device) as an opt-in unlock for unlimited/heavy chat use. New `feature/ai` module. Not yet designed/grilled — greenfield. Revised 2026-07-05: pure BYOK was rejected because the target audience (couples budgeting, PH market) is non-technical and won't self-serve an API key, which would leave the feature effectively unused; the hybrid trades some cost predictability for actual adoption. Possible sub-features: financial companion chat, smart insights, NL transaction entry, receipt OCR. **Target: not yet determined** — greenfield, not yet designed/grilled.
4. **Password vault** — new `feature/vault` module, SQLCipher/EncryptedDataStore. **Target: not yet determined** — no active design or demand signal yet.
5. **Voice recording storage** — new `feature/recordings` module + Supabase Storage. **Target: Q3 2026.**
6. **iOS** — evaluate Kotlin Multiplatform (domain layer already pure Kotlin). **Target: not yet determined** — pending the KMP evaluation itself.
7. **CSV / PDF export** — data already structured for it. **Target: Q3 2026.**
8. **Custom fonts** — typography customization beyond the built-in color themes. (Category/account icon customization already shipped in V1.3 — see the V1.3 row above — so this is fonts only now, not icon packs.) **Target: Q3 2026.**
9. **Profile & couple photo upload** — avatar/banner images via Supabase Storage. **Target: Q3 2026.**
10. **Change password / change email while logged in** — Settings has no in-app path to either; only the recovery-flow "forgot password" (V1.6.1 Item 3) exists, and that requires signing out first. Internal-only (account/compliance housekeeping) — not surfaced on the tester-facing roadmap, no public target.
11. **Delete my account** — compliance/account-management to-do (likely a Play Store Data Safety requirement once this reaches prod), not a tester-facing feature. Needs an RPC per ADR-0006/0008's couple-ops-are-RPCs-only rule (unpairing the partner is part of deleting a paired user), plus a decision on self-service vs. support-mediated. Internal-only — no public target.
12. **Login rate limiting / lockout** — the local PIN lockout (V1.6.1 Item 8) only gates the on-device app-lock; the Supabase Auth sign-in screen itself (email+password) has no client-side attempt cap. Needs a decision on whether Supabase's own server-side rate limiting already covers this or a client-side cooldown is also wanted. Internal-only — no public target.
13. **"Restart fresh": reset/wipe user finances from Settings** — moved here 2026-07-05 from [v1.6.2.md](v1.6.2.md) Item 1 (deferred out of the V1.6.2 batch). Unlike items 1-12, this one is **already fully grilled and ready to build** — decisions locked in ADR-0037 + `CONTEXT.md` ("Reset finances"): a `ResetFinancesUseCase` soft-deletes the user's own transactions/recurring rules/budgets/goal contributions (owned-rows-only, single `@Transaction`) then pushes tombstones, keeping accounts/categories/savings-goal definitions/opening balances/notes. Distinct from Horizon #11 "Delete my account" (they share only a generic bulk-soft-delete helper). See [v1.6.2.md](v1.6.2.md) Item 1 for the full grilled design. **Target: Q3 2026** — the most build-ready item on this list.
14. **Display-currency symbol (non-PHP)** — moved here 2026-07-05 from [v1.6.3.md](v1.6.3.md) Item 3 (deferred out of the V1.6.3 batch; grilled/scoped, not built). **Display-symbol-only, NOT multi-currency:** a single cosmetic setting that swaps the `₱` glyph for another symbol — **no per-account currency, no FX conversion**; all amounts stay one currency underneath (true multi-currency collides with the PHP-only V1 convention and would need a currency field on every money row plus a decision on what the combined couple view sums — explicitly out of scope). **Chosen at onboarding.** Combined view uses the **viewer's** symbol (personal display pref like theme, cosmetic, deliberately **not** reconciled across the couple — a partner picking a different symbol just sees their own label). **Target: Q3 2026.**

Added 2026-07-05 (items 10-12, requested by Alvin alongside the V1.6.1 additions below; item 13 deferred from V1.6.2 same day; item 14 deferred from V1.6.3 same day). **2026-07-05 addendum, revised 2026-07-06:** provisional public target quarters assigned above for the subset surfaced on the tester-facing Settings → Upcoming features screen (items 1, 2, 5, 7, 8, 9, 13, 14 — all **Q3 2026**); items 3 (AI companion), 4 (Password vault), and 6 (iOS) stay explicitly **not yet determined**; items 10-12 stay internal-only and don't appear on that screen. These are planning targets, not commitments, and can move — this doc is the single source of truth for them; `PRD.md`/`ARCHITECTURE.md` list the same 14 items without duplicating the dates, and `UpcomingFeaturesScreen.kt` mirrors the tester-facing subset above.

---

## Living reference

These sections describe cross-version state that changes incrementally — update them in place rather than duplicating across version docs.

### Infra state

Supabase project `vyjaorlevomfqkidttom.supabase.co` (Singapore, **staging** — prod Supabase doesn't exist yet, see memory `staging-prod-environment`). Schema applied, auth working, sync wired end-to-end. All pre-ship infra was verified live 2026-06-27: V1.3 schema (icons/receipts columns+views), both storage buckets (`note-images`, `receipts`, private), and all RLS policies (`realtime.messages`/`couple_channel_members`, storage owner/partner-read, `accounts_couple`/`categories_couple`/`budgets_couple`). Email confirmation is on in Auth settings.

`supabase/migrations/` is a complete, ordered replay log (apply in filename date order):
1. `2026-06-27_shared_accounts_categories.sql`
2. `2026-07-03_savings_goals_schema.sql` (original slice-9 shape, pre-RLS-split/pre-membership-check)
3. `2026-07-03_unpair_broadcast_bell.sql`
4. `2026-07-03_unpair_reconcile.sql`
5. `2026-07-03_goal_contributions_rls_split.sql` ([F1](v1.5-post-ship-audit.md))
6. `2026-07-03_partner_goal_contributions_membership.sql` ([F3](v1.5-post-ship-audit.md))
7. `2026-07-04_backfill_stale_shared_goals.sql` ([V1.6](v1.6.md))
8. `2026-07-04_redeem_invite_restamp_inviter.sql` (re-stamps the inviter's `users` row past the redeemer's pull cursor so the partner row actually lands locally after pairing)
9. `2026-07-04_app_release_info.sql` ([V1.6.1 Item 9](v1.6.1.md))
10. `2026-07-04_transfer_fee.sql` ([V1.6.1 Item 12](v1.6.1.md))
11. `2026-07-05_recurring_pause_skip.sql` ([V1.6.1 Item 18](v1.6.1.md))
12. `2026-07-05_budget_rollover.sql` ([V1.6.2 Item 5](v1.6.2.md))
13. `2026-07-06_notes_is_pinned.sql` ([V1.6.3 Item 1](v1.6.3.md), ADR-0040)
14. `2026-07-06_partner_debt_missing_columns.sql` ([V1.6.3 Item 4](v1.6.3.md) — closes staging schema drift on `partner_debts.source_transaction_id` / `partner_debt_payments.is_netting`, `counter_debt_id`)

All confirmed applied live (#13/#14 verified 2026-07-06 via PostgREST column probes; #8/#12 were re-verified 2026-07-05 after they'd silently drifted out of the "applied" record despite being real, committed, shipped features — #12 in particular meant every rollover-enabled budget save had been failing to push since `50d9a92`). `supabase/schema.sql` remains the authoritative end-state.

### Room version

**v22** is the last *committed* version (V1.6.3 Item 1: `notes.is_pinned` via `@AutoMigration(21→22)`). History: v10 baseline (V1 slice K) → v11 (slice E, note images) → v12 (slice F, note sharing) → v13 (V1.3 #13, receipts) → v14 (V1.3 #9, debt netting) → v15 (V1.3 #12, paid-on-behalf) → v16 (V1.3 #14, settlements) → v17 (V1.3 #11, shared accounts/categories) → v18 (V1.5 #9, savings goals) → v19 (V1.6.1 #12, transfer fee link) → v20 (V1.6.1 Item 18, recurring `is_paused` only — "skip" advances `next_date` instead of storing a column, no `skipped_until` ever existed) → v21 (V1.6.2 Item 5, budgets `rollover_enabled`) → v22 (V1.6.3 Item 1, notes `is_pinned`). (An earlier, unrelated v22 `budget_templates` table was built for a V1.6.2 Item 6 approach, then fully reverted same day before this v22 was assigned to `is_pinned`.)

### supabase-kt 3.x API notes

- `upsert(rows: List<T>)` is a `suspend fun` returning `Unit` — no `.execute()` chaining.
- `select { filter { gt("col", cursor) }; order("col", Order.ASCENDING); limit(n.toLong()) }.decodeList<T>()` — `select {}` returns a builder; `decodeList<T>()` executes.
- `WorkManager.enqueueUniqueWork()` requires `OneTimeWorkRequest`, not `WorkRequest`.

### Per-feature pattern

Copy from any existing feature. `feature/x/` → `data/local` (Entity impl `SyncMeta` + DAO), `data/remote` (Dto `@Serializable` + RemoteSource port + `SupabaseXRemoteSource`), `data/XMapper.kt` (toDomain/toDto/toEntity), `XRepositoryImpl`, `data/sync/XTableSyncer`; `domain/model|repository|usecase`; `presentation/`. Then: add to `IponDatabase` + bump the Room version, add the DAO to `DatabaseModule`, add `XModule` (`@Binds`), add the route to `IponApp`.
