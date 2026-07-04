# Love, Ipon — Claude Code Context

## How to Start Each Conversation

**Read exactly these, then start coding — nothing else:**
1. `docs/build/project-build-progress.md` — current state + index of per-version docs (`docs/build/v1.md`, `v1.1.md`, ...); open whichever version doc covers what's in flight
2. One reference feature folder (e.g. `app/src/main/java/com/iponlove/app/feature/budgets/`) — for copy-paste pattern
3. The relevant table(s) in `supabase/schema.sql` — for the new entity/DTO shape

**Do NOT read:** `PRD.md`, `ARCHITECTURE.md`, `CONTEXT.md`, or browse the folder tree. `CLAUDE.md` (this file) already covers everything needed. `docs/build/` covers build state.

**After each feature slice is committed:** update `docs/build/project-build-progress.md`'s "Current state" section, and append the slice to the current version's `docs/build/vX.Y.md` (or start a new one for a new version). Bump the Room version note in `project-build-progress.md` if it changed. This is the single source of truth for orientation in the next conversation.

---

## Which Model to Use

The rule: **Sonnet by default, Opus when the design is genuinely novel or architecturally risky.**

**REQUIRED: State the model choice and your rationale in one sentence before writing any code or making any edit.**

**Use Sonnet for:**
- Bug fixes and UI polish — mechanical, pattern-following
- Additive post-V1 features that follow the established slice pattern (new entity → DAO → syncer → usecase → screen)
- Any task where the design is clear and the risk is low

**Use Opus for:**
- Cross-cutting architectural redesigns (e.g. the pending sync overhaul — Realtime vs polling, per-table triggers, optimistic UI)
- Any new feature with non-obvious cross-ADR interactions (e.g. AI companion reading across all entities, shared lists extending the couple-sharing layer)
- Debugging subtle sync or concurrency bugs — Opus reasons through multi-step state better
- Any slice where you're unsure how the design fits together before writing code (run `/grilling` first, then build on Opus)

**General principle:** If the task is "follow the pattern," use Sonnet. If it spans multiple ADRs, involves shared state between users, or requires architectural decisions, use Opus. Sonnet is faster and cheaper for mechanical work; the saving pays for Opus where it matters.

---

## What This App Is
A couples personal finance + notes Android app for the Philippine market. Users track individual expenses and share a combined financial view with their partner. Clean, aesthetic UI. Offline-first with Supabase cloud sync.

---

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3 — no XML layouts ever
- **DI:** Hilt
- **Async:** Coroutines + StateFlow — never LiveData, never RxJava
- **Local DB:** Room (offline-first source of truth)
- **Backend:** Supabase (Auth, Postgres, Storage)
- **Background sync:** WorkManager
- **Preferences:** Jetpack DataStore
- **Widget:** Jetpack Glance
- **Image loading:** Coil
- **Rich text:** Compose Rich Editor (monospacedmonkey)
- **Build:** Gradle Kotlin DSL + Version Catalogs (libs.versions.toml)

No Firebase. No XML. No LiveData. No RxJava.

---

## Architecture
MVVM + Clean Architecture, feature-based folder structure.

```
UI Layer      → Composables + ViewModel + UiState
Domain Layer  → UseCases + Repository interfaces + Domain models (pure Kotlin)
Data Layer    → RepositoryImpl + Room DAOs + Supabase remote sources
```

- ViewModels expose `StateFlow<UiState>` only
- No business logic in Composables
- Domain layer has zero Android imports
- UI never accesses Repository or UseCase directly

Feature folder structure:
```
feature_x/
  data/local, data/remote, XRepositoryImpl, XMapper
  domain/model, domain/repository (interface), domain/usecase
  presentation/XViewModel, XScreen, XUiState, components/
```

---

## Workflow (how we build)
- **Vertical slices, not layers.** Build one feature end-to-end (entity → DAO → repo → usecase → ViewModel → screen) and verify it runs before starting the next. Don't build five half-finished layers across features.
- **One concern per change.** Prefer "build the Accounts feature" over bundling multiple features.
- **Keep the build green.** Run a build after each slice; never let compile errors pile up across features.
- **Commit after each green slice.** Each working feature = one commit — it's the undo button when an AI edit goes wrong.
- **HARD RULE — never commit or push without my explicit permission.** Stage and propose, but do not run `git commit` or `git push` until I say so each time. Permission granted once does not carry over to the next commit/push.
- **Verify UI by running the app**, not by eyeballing the code.

## Testing Policy
The per-commit gate is: **build compiles green**, and **domain + data logic has unit tests**. UI is verified by running, not unit-tested, until it stabilizes. Keep the unit suite JVM-only and seconds-fast (no emulator) so it actually gets run.

- **Always test (high bug-risk, cheap — pure Kotlin, JVM):** sync / conflict resolution (row-level LWW by `updated_at`, dirty-flag push selection, `server_rev` pull cursor, partner-row purge/conflict-copy merge cases), money & budget math, derived balance, analysis aggregations, recurring-rule date math, mappers (Entity↔Domain↔DTO), UseCases.
- **Test once stable:** ViewModels.
- **Don't unit-test early:** Composables/UI (churns during design; verify by running). A few Room DAO instrumented tests only for complex queries.
- Write tier-1 tests alongside the slice that introduces the logic — especially sync and money math.
- Stack: JUnit + Truth (assertions) + Turbine (Flow/StateFlow) + MockK + kotlinx-coroutines-test.

## Build / Run Commands
- Build debug APK: `./gradlew assembleDebug`
- Run unit tests (fast, JVM): `./gradlew testDebugUnitTest`
- Install on running device/emulator: `./gradlew installDebug`
- Lint: `./gradlew lintDebug`
- JDK 21 (Android Studio JBR), `compileSdk = 37` (android-37.0 stable platform), `targetSdk = 35`. AGP 9.2.1 / Gradle 9.6 / Kotlin 2.2.10 (built-in via AGP).

---

## Key Conventions
- Currency: PHP only — no multi-currency
- Deletes are always soft (`is_deleted = true`) — never hard delete for sync safety; tombstones kept indefinitely (ADR-0010)
- Every write sets `updated_at = max(now() + clockOffset, prev + 1ms)` (offset-corrected, monotonic LWW key — ADR-0001) and `pending_sync = true` (local-only outbox flag — ADR-0002)
- Sync is manual (app foreground, network reconnect, pull-to-refresh) — not real-time. Push = dirty `pending_sync` rows; pull = `server_rev > cursor`. Conflict = row-level LWW by `updated_at`, except shared notes (conflict copy). See ADR-0002/0003 and `docs/adr/`.
- Interactive sync (pull-to-refresh, foreground) runs in-process for immediacy; WorkManager owns background retry/reconnect (ADR-0012)
- Account balance is derived (opening_balance + ledger), never synced (ADR-0007)
- Partner data is read via redacting views and replicated into Room; combined view shows shared spending, not partner balances (ADR-0004/0005/0011)
- Room is always read first; Supabase is background sync only
- Money amounts use `BigDecimal` — never `Double` or `Long`; serialize via `BigDecimalSerializer` for Supabase DTOs
- Package name: `com.iponlove.app`
- minSdk: 26 (Android 8.0)

---

## Critical Decisions (ADRs)

Full rationale in `docs/adr/`. These are the rules most likely to be violated by a cold-start agent — know them before touching sync, couples, or auth.

**ADR-0006/0008 — Couple ops are RPCs only.** Never write directly to the `couples` table. Use `create_couple`, `redeem_invite`, `rotate_invite_code`, `unpair` server-side RPCs. Unpair also triggers a local bulk purge of all replicated non-owned rows.

**ADR-0013 — Users row is a synced entity.** `EnsureCurrentUserRowUseCase` runs on login before any other write. No database trigger creates the row. The users row must push before accounts/categories (FK root in the ordering below).

**ADR-0005 — Partner data goes through redacting views.** Never query partner base tables directly. A partner row arriving flagged private, deleted, or unshared means purge the local Room copy, not upsert it.

**ADR-0009 — FK push/pull order.** Always process tables in this order (both push and pull): `users → couples → accounts → categories → recurring_rules → transactions → budgets → notes → note_images` (partner variants follow their owned counterparts). Upserts are idempotent by `id`; an interrupted sync just resumes.

**ADR-0014 — Personalize screen.** Live preview is local ViewModel state only — do not persist on tap. Save/Apply writes to DataStore. Couple attribution color (blue/pink in combined view) is stored as `accent_color` on the `users` row, chosen during the pairing flow — it is separate from the personal theme palette.

---

## Auth
- Email + password via Supabase Auth only (v1)
- Email verification required before first login
- PIN and biometric are local app lock only — not server auth; lock triggers after 30 seconds in background (grace period in `AppLockManager`)
- Session stored in DataStore; Supabase SDK handles token refresh

---

## V1 Scope
- Budget tracker (transactions, accounts, categories, budgets, analysis, recurring)
- Notes (rich text, checklists, images, optional partner sharing)
- Couples pairing via invite code, combined view with color-coded attribution
- Shared couple budget (joint monthly budget)
- Partner Debt Tracker (couples-only IOU tracking; hidden until paired)
- App lock (PIN + biometric; local only; 30-second grace period)
- Multiple themes (light + dark + more)
- Balance home screen widget + quick-add shortcut widget
- Minimal notifications (budget alerts only)

## Scalability Principle

When in doubt: favor thin, composable layers over shortcuts. Three constraints that must survive into post-V1:

- **UseCases own data access** — never scatter queries into ViewModels; a future export UseCase must be able to reuse them
- **Encryption utilities in `core/`** — not buried inside `feature/vault/`; other features may need them
- **Sharing logic stays generic** — the pairing/sharing infrastructure must not be hard-coded to notes only; shared lists and other future shared entities must be addable without rewrites

---

## Explicitly Out of Scope for V1
- Google / Facebook SSO
- AI chatbot
- Password vault
- Voice recording
- CSV / PDF export
- iOS
- Custom fonts (typography only — category/account icon customization already shipped in V1.3; themes remain color-only otherwise)
- Profile / couple photo upload (avatars are accent color + initials in V1)

---

## Monetization
Paid app on Play Store (one-time purchase). All features unlocked on purchase. No tiers, no IAP, no subscriptions.

---

## Bottom Nav Tabs
Records | Analysis | Budgets | Accounts | Categories
(same layout as MyMoney app — reference for UX feel)

**Outside the bottom nav:**
```
Auth graph        → Login → Register → Forgot password
Onboarding graph  → Profile setup → Couple setup
Notes             → Notes list → Note editor
Couple view       → Combined view → Shared budget → Partner Debt Tracker
Settings          → Theme / Profile / Couple / Notifications
Transaction entry → Add / Edit (bottom sheet modal over any tab)
```

---

## Supabase Config
- Region: ap-southeast-1 (Singapore)
- Auth: email + password
- Storage: note image attachments only (no avatar/profile photo upload in V1)
