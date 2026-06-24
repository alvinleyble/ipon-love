# Ipon, Love — Claude Code Context

## How to Start Each Conversation

**Read exactly these, then start coding — nothing else:**
1. `memory/project-build-progress.md` — current build state, what's built, what's next, the per-feature pattern
2. One reference feature folder (e.g. `app/src/main/java/com/iponlove/app/feature/budgets/`) — for copy-paste pattern
3. The relevant table(s) in `supabase/schema.sql` — for the new entity/DTO shape

**Do NOT read:** `PRD.md`, `ARCHITECTURE.md`, `CONTEXT.md`, or browse the folder tree. `CLAUDE.md` (this file) already covers everything needed. `memory/project-build-progress.md` covers build state.

**After each feature slice is committed:** update `memory/project-build-progress.md` — add the new slice to the committed list, bump Room version if changed, update the "V1 still to build" list. This file is the single source of truth for orientation in the next conversation.

---

## What This App Is
A couples personal finance + notes Android app for the Philippine market. Users track individual expenses and share a combined financial view with their partner. Clean, aesthetic UI. Offline-first with Supabase cloud sync.

Personal instance is branded **PattyWallet**. Public Play Store name is **Ipon, Love**.

Full specs: see PRD.md and ARCHITECTURE.md.

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
- Package name: `com.iponlove.app`
- minSdk: 26 (Android 8.0)

---

## Auth
- Email + password via Supabase Auth only (v1)
- Email verification required before first login
- PIN and biometric are local app lock only — not server auth
- Session stored in DataStore; Supabase SDK handles token refresh

---

## V1 Scope
- Budget tracker (transactions, accounts, categories, budgets, analysis, recurring)
- Notes (rich text, checklists, images, optional partner sharing)
- Couples pairing via invite code, combined view with color-coded attribution
- Shared couple budget (joint monthly budget)
- Multiple themes (light + dark + more)
- Home screen widget
- Minimal notifications (budget alerts only)

## Scalability Principle

Every V1 decision must leave the door open for post-V1 features — no rewrites, just additions.

Planned post-V1 enhancements to keep in mind:
- **AI financial companion** — will need access to transaction history; keep domain models query-friendly and don't bury business logic in the DB layer
- **Receipt / photo on transactions** — Supabase Storage is already in the stack; Transaction entity should have a nullable `attachmentUrl` field from day one
- **Password vault** — will be a new feature module; no coupling concerns, but encryption utilities should live in a shared `core` module
- **Voice recording on notes** — Notes data model should use a generic `attachments` concept, not be hard-coded to images only
- **iOS / Kotlin Multiplatform** — keep the domain layer free of Android imports (already a rule); avoid Android-only types leaking into domain models
- **CSV / PDF export** — keep data access in UseCases, not scattered across ViewModels, so an export UseCase can reuse the same queries
- **Shared lists (groceries, trip budgets)** — the couple-sharing and notes infrastructure built in V1 is the foundation; don't hard-code sharing logic to notes only

When in doubt: favor thin, composable layers over shortcuts. A feature being out of scope for V1 does not mean we design against it.

---

## Explicitly Out of Scope for V1
- Google / Facebook SSO
- AI chatbot
- Password vault
- Voice recording
- Receipt photo on transactions
- CSV / PDF export
- iOS
- Custom fonts / custom category icon packs (themes are color-only in V1)
- Profile / couple photo upload (avatars are accent color + initials in V1)

---

## Monetization
Paid app on Play Store (one-time purchase). All features unlocked on purchase. No tiers, no IAP, no subscriptions.

---

## Bottom Nav Tabs
Records | Analysis | Budgets | Accounts | Categories
(same layout as MyMoney app — reference for UX feel)

---

## Supabase Config
- Region: ap-southeast-1 (Singapore)
- Auth: email + password
- Storage: note image attachments only (no avatar/profile photo upload in V1)
