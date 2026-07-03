# Love, Ipon — Build Progress

**This is the cold-start orientation file** (see `CLAUDE.md`'s "How to Start Each Conversation"). Read this file first, then the version doc for whatever's in flight, then one reference feature folder and the relevant `supabase/schema.sql` table before writing code.

**Update this file's "Current state" section (and the relevant `vX.Y.md` file) after every commit.** This is the single source of truth for orientation in the next conversation — keep it current, not the git log.

---

## Current state (as of 2026-07-04)

**V1 through V1.6 are all committed on `main`.** Nothing pending on the V1.5 post-ship audit (all 11 items closed, see [v1.5-post-ship-audit.md](v1.5-post-ship-audit.md)). [V1.6.1](v1.6.1-plan.md) is in planning: Item 1 (misleading "(prod)" beta-feedback version tag) is fixed but **uncommitted** (`BetaFeedbackViewModel.kt`); Items 2–5 are still `TODO`/`NEEDS DECISION`.

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
| V1.6.1 | Planning — beta version tag, Records compactness, forgot password, navbar editor bug, Settings "Beta" section | [v1.6.1-plan.md](v1.6.1-plan.md) |

**Post-V1 Horizon** — the only work beyond the numbered versions above, unscheduled by design (could land before or after production launch depending on priority/capacity):

1. **Google Sign-In** — Supabase OAuth + Android Credential Manager
2. **Facebook Login** — Supabase OAuth + Facebook SDK
3. **AI companion** — BYOK (user's own Anthropic key, encrypted on-device), new `feature/ai` module. Not yet designed/grilled — greenfield. Monetization is one-time purchase (no subs) → BYOK avoids ongoing inference cost. Possible sub-features: financial companion chat, smart insights, NL transaction entry, receipt OCR.
4. **Password vault** — new `feature/vault` module, SQLCipher/EncryptedDataStore
5. **Voice recording storage** — new `feature/recordings` module + Supabase Storage
6. **iOS** — evaluate Kotlin Multiplatform (domain layer already pure Kotlin)
7. **CSV / PDF export** — data already structured for it
8. **Delete account** — user-initiated permanent account deletion (Settings). Not yet designed/grilled. Non-obvious cross-cutting concerns to grill first: Supabase Auth user deletion (needs a server-side/edge function, can't self-delete via client SDK), couple unpair/cleanup if paired (reuse `unpair()` RPC, ADR-0006/0008), cascade behavior for owned rows (hard delete vs. soft-delete-forever per ADR-0010 — this is likely the one legitimate exception), partner's replicated copies, and local Room wipe (`LocalDataWiper`, already exists for sign-out/account-switch). Likely Opus + `/grilling` given the ADR-0010/ADR-0006 interactions.

No timeline or grilled design exists for any of the 8 yet.

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

All confirmed applied live as of their respective commits. `supabase/schema.sql` remains the authoritative end-state.

### Room version

**v18** (V1.5 slice 9: `savings_goals` + `goal_contributions` via `@AutoMigration(17→18)`). History: v10 baseline (V1 slice K) → v11 (slice E, note images) → v12 (slice F, note sharing) → v13 (V1.3 #13, receipts) → v14 (V1.3 #9, debt netting) → v15 (V1.3 #12, paid-on-behalf) → v16 (V1.3 #14, settlements) → v17 (V1.3 #11, shared accounts/categories) → v18 (V1.5 #9, savings goals).

### supabase-kt 3.x API notes

- `upsert(rows: List<T>)` is a `suspend fun` returning `Unit` — no `.execute()` chaining.
- `select { filter { gt("col", cursor) }; order("col", Order.ASCENDING); limit(n.toLong()) }.decodeList<T>()` — `select {}` returns a builder; `decodeList<T>()` executes.
- `WorkManager.enqueueUniqueWork()` requires `OneTimeWorkRequest`, not `WorkRequest`.

### Per-feature pattern

Copy from any existing feature. `feature/x/` → `data/local` (Entity impl `SyncMeta` + DAO), `data/remote` (Dto `@Serializable` + RemoteSource port + `SupabaseXRemoteSource`), `data/XMapper.kt` (toDomain/toDto/toEntity), `XRepositoryImpl`, `data/sync/XTableSyncer`; `domain/model|repository|usecase`; `presentation/`. Then: add to `IponDatabase` + bump the Room version, add the DAO to `DatabaseModule`, add `XModule` (`@Binds`), add the route to `IponApp`.
