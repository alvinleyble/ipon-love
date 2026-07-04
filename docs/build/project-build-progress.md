# Love, Ipon — Build Progress

**This is the cold-start orientation file** (see `CLAUDE.md`'s "How to Start Each Conversation"). Read this file first, then the version doc for whatever's in flight, then one reference feature folder and the relevant `supabase/schema.sql` table before writing code.

**Update this file's "Current state" section (and the relevant `vX.Y.md` file) after every commit.** This is the single source of truth for orientation in the next conversation — keep it current, not the git log.

---

## Current state (as of 2026-07-05)

**V1 through V1.6 are all committed on `main`.** Nothing pending on the V1.5 post-ship audit (all 11 items closed, see [v1.5-post-ship-audit.md](v1.5-post-ship-audit.md)). [V1.6.1](v1.6.1.md)'s original 12-item batch is **feature-complete, pending Alvin's commit approval** — all 12 resolved. **2026-07-05: Alvin added three more items to the same doc** — Item 13 (Notes as its own module), Item 14 (consistent tab-tap navigation), Item 15 (first-run tutorial/coach-marks). **Second batch same day:** Item 16 (Privacy Policy/ToS), Item 17 (search, for consideration), Item 18 (recurring pause/skip), Item 19 (budget rollover). **Grilling pass, same day:** Items 13, 14, 15, 18, 19 fully grilled and locked in — see ADR-0033 (nav rework), ADR-0034 (tutorial), ADR-0035 (recurring pause/skip), ADR-0036 (budget rollover), plus new `CONTEXT.md` glossary terms. Item 16 resolved inline (Privacy Policy already hosted, no ToS needed for V1). **All of 13-16 and 18-19 are now `TODO`, ready to build** — order: 14 → 13 → 15 (hard nav dependency), then 18, 19 (independent), then 16 (trivial). **Item 17 (search) is `DROPPED` — cut entirely.** Item 1 is `DONE fa3bff4` (misleading "(prod)" beta-feedback version tag). Item 4 is `DONE 8fe6c83` (navbar pairing redesign, grew to also fix a cross-account PIN/data leak). Item 5 is `DONE f909697` (Settings "Beta" section + Upcoming features page). Item 8 is `DONE a5b12d2` (PIN lockout — 5-attempt threshold + 30s cooldown). Item 11 is `DONE d212361` (Analysis period presets — Quarter/Semi-annual/Annual/All-time, redesigned period selector). Item 2 is `DONE 20a2482` (Records/Combined bounded to a stepped calendar month with sticky day headers, shared `core/date/MonthWindow.kt`/`DayGrouping.kt` math — ADR-0032). Item 9 is `DONE 5aafe7a` (force update on version mismatch), confirmed on-device by Alvin. Item 12 is `DONE a41c154` (transfer fee as a cascading linked expense, new `SaveTransferUseCase`, Room v19 — ADR-0031), confirmed on-device by Alvin. Item 3 is `DONE b1688d6` (forgot-password flow — new `AuthStatus.PasswordRecovery`, `ForgotPasswordScreen`/`ResetPasswordScreen`; live debugging found the `Auth` plugin was missing `scheme`/`host` config, silently dropping every deep link, plus `AuthErrorClassifier` gaps for GoTrue's weak/same-password messages — all fixed), confirmed on-device by Alvin. Item 10 is `DONE 4d1f670` (added an explanatory caption below the "Private" toggle in `AddTransactionScreen.kt`, matching "Paid for partner"'s style), confirmed on-device by Alvin. **Item 6/7 is `DONE c59111c`** — biometric unlock fixed 2026-07-05, confirmed working on Alvin's Nothing Phone 2a. Real root cause (found via on-device Logcat, not static analysis alone): `LockScreen.kt`'s `context as? FragmentActivity` cast silently failed on every call because the lock screen renders inside a `Dialog`, and the project's actually-resolved `androidx.compose.ui:ui-android:1.11.3` (not the `1.7.5` an earlier static-analysis pass happened to check) wraps that Dialog's context in a `ContextThemeWrapper` — fixed with a `Context.findActivity()` `ContextWrapper`-chain unwrap. See [v1.6.1.md](v1.6.1.md) Item 6 for the full investigation record, including three other real (secondary) bugs found and fixed along the way in the same code path.

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
| V1.6.1 | Original 12-item batch feature-complete, pending commit approval; 7 more items added 2026-07-05 (Notes-as-module, tab navigation, tutorial, Privacy Policy/ToS, search, recurring pause/skip, budget rollover) — 6 of 7 fully grilled and `TODO`-ready (ADR-0033 through ADR-0036), search (Item 17) deferred by choice | [v1.6.1.md](v1.6.1.md) |

**Post-V1 Horizon** — the only work beyond the numbered versions above, unscheduled by design (could land before or after production launch depending on priority/capacity). Reconciled 2026-07-04 against `PRD.md`/`ARCHITECTURE.md` (which had drifted out of sync with each other and with this list — see `v1.6.1.md` Item 5):

1. **Google Sign-In** — Supabase OAuth + Android Credential Manager
2. **Facebook Login** — Supabase OAuth + Facebook SDK
3. **AI companion** — hybrid, not pure BYOK: a capped, app-funded free tier (cheap model, e.g. Haiku, small monthly message allowance) for the mass-market sub-features (smart insights, NL transaction entry), with BYOK (user's own Anthropic key, encrypted on-device) as an opt-in unlock for unlimited/heavy chat use. New `feature/ai` module. Not yet designed/grilled — greenfield. Revised 2026-07-05: pure BYOK was rejected because the target audience (couples budgeting, PH market) is non-technical and won't self-serve an API key, which would leave the feature effectively unused; the hybrid trades some cost predictability for actual adoption. Possible sub-features: financial companion chat, smart insights, NL transaction entry, receipt OCR.
4. **Password vault** — new `feature/vault` module, SQLCipher/EncryptedDataStore
5. **Voice recording storage** — new `feature/recordings` module + Supabase Storage
6. **iOS** — evaluate Kotlin Multiplatform (domain layer already pure Kotlin)
7. **CSV / PDF export** — data already structured for it
8. **Custom fonts** — typography customization beyond the built-in color themes. (Category/account icon customization already shipped in V1.3 — see the V1.3 row above — so this is fonts only now, not icon packs.)
9. **Profile & couple photo upload** — avatar/banner images via Supabase Storage
10. **Change password / change email while logged in** — Settings has no in-app path to either; only the recovery-flow "forgot password" (V1.6.1 Item 3) exists, and that requires signing out first.
11. **Delete my account** — compliance/account-management to-do (likely a Play Store Data Safety requirement once this reaches prod), not a tester-facing feature. Needs an RPC per ADR-0006/0008's couple-ops-are-RPCs-only rule (unpairing the partner is part of deleting a paired user), plus a decision on self-service vs. support-mediated.
12. **Login rate limiting / lockout** — the local PIN lockout (V1.6.1 Item 8) only gates the on-device app-lock; the Supabase Auth sign-in screen itself (email+password) has no client-side attempt cap. Needs a decision on whether Supabase's own server-side rate limiting already covers this or a client-side cooldown is also wanted.

Added 2026-07-05 (items 10-12, requested by Alvin alongside the V1.6.1 additions below). No timeline or grilled design exists for any of the 12 yet.

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
8. `2026-07-04_transfer_fee.sql` ([V1.6.1 Item 12](v1.6.1.md))

All confirmed applied live as of their respective commits. `supabase/schema.sql` remains the authoritative end-state.

### Room version

**v19** (V1.6.1 Item 12: `transactions.transferFeeTransactionId` via `@AutoMigration(18→19)`). History: v10 baseline (V1 slice K) → v11 (slice E, note images) → v12 (slice F, note sharing) → v13 (V1.3 #13, receipts) → v14 (V1.3 #9, debt netting) → v15 (V1.3 #12, paid-on-behalf) → v16 (V1.3 #14, settlements) → v17 (V1.3 #11, shared accounts/categories) → v18 (V1.5 #9, savings goals) → v19 (V1.6.1 #12, transfer fee link).

### supabase-kt 3.x API notes

- `upsert(rows: List<T>)` is a `suspend fun` returning `Unit` — no `.execute()` chaining.
- `select { filter { gt("col", cursor) }; order("col", Order.ASCENDING); limit(n.toLong()) }.decodeList<T>()` — `select {}` returns a builder; `decodeList<T>()` executes.
- `WorkManager.enqueueUniqueWork()` requires `OneTimeWorkRequest`, not `WorkRequest`.

### Per-feature pattern

Copy from any existing feature. `feature/x/` → `data/local` (Entity impl `SyncMeta` + DAO), `data/remote` (Dto `@Serializable` + RemoteSource port + `SupabaseXRemoteSource`), `data/XMapper.kt` (toDomain/toDto/toEntity), `XRepositoryImpl`, `data/sync/XTableSyncer`; `domain/model|repository|usecase`; `presentation/`. Then: add to `IponDatabase` + bump the Room version, add the DAO to `DatabaseModule`, add `XModule` (`@Binds`), add the route to `IponApp`.
