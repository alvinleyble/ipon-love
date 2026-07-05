# Architecture Document — Ipon, Love

**Version:** 1.2
**Date:** June 2026
**Companion doc:** PRD.md
**Status:** Clarified — pre-grilling pass

---

## 1. Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | Kotlin | Android standard |
| UI | Jetpack Compose + Material 3 | Modern Android UI, no XML |
| Navigation | Compose Navigation | Type-safe, Compose-native |
| DI | Hilt | Google-standard, Android lifecycle-aware |
| Async | Coroutines + StateFlow | Kotlin-native, pairs with ViewModel |
| Local DB | Room | Offline-first source of truth |
| Backend | Supabase | Postgres + Auth, familiar stack (Postgres/Render background) |
| Auth | Supabase Auth (email + password) | Zero prerequisite setup, Supabase handles everything |
| Image loading | Coil | Compose-native, lightweight |
| Background sync | WorkManager | Reliable background execution on Android |
| Preferences | Jetpack DataStore | Replaces SharedPreferences, async-safe |
| Widget | Jetpack Glance | Compose-based home screen widgets |
| Rich text editor | Compose Rich Editor (monospacedmonkey) | Compose-native rich text for notes |
| Build system | Gradle (Kotlin DSL) + Version Catalogs (libs.versions.toml) | Centralized dependency management |

No Firebase. No RxJava. No XML layouts.

---

## 2. Architecture Pattern

**MVVM + Clean Architecture**, feature-based module structure.

```
UI Layer      →  Composables + ViewModel + UiState
Domain Layer  →  UseCases + Repository interfaces + Domain models
Data Layer    →  RepositoryImpl + Room DAOs + Supabase remote sources
```

Rules:
- UI knows only ViewModel. Never accesses Repository or UseCase directly.
- Domain has zero Android dependencies. Pure Kotlin only.
- Data layer is the only layer that knows about Room or Supabase.
- ViewModels expose `StateFlow<UiState>`. Never `LiveData`.
- No business logic inside Composables.

---

## 3. Folder Structure

```
app/src/main/java/com/iponlove/app/
│
├── core/                           # Shared across all features
│   ├── database/
│   │   ├── IponDatabase.kt         # Room database definition
│   │   └── converters/             # Type converters (Date, enums, etc.)
│   ├── network/
│   │   └── SupabaseClient.kt       # Supabase client singleton
│   ├── auth/
│   │   └── AuthManager.kt          # Session management, PIN/biometric
│   ├── sync/
│   │   └── SyncManager.kt          # WorkManager sync orchestration
│   ├── ui/
│   │   ├── theme/
│   │   │   ├── Theme.kt
│   │   │   ├── Color.kt
│   │   │   └── Type.kt
│   │   └── components/             # Shared Composables (buttons, cards, etc.)
│   ├── datastore/
│   │   └── UserPreferences.kt      # Theme selection, PIN hash, session prefs
│   └── di/                         # Hilt modules
│       ├── DatabaseModule.kt
│       ├── NetworkModule.kt
│       └── RepositoryModule.kt
│
├── feature/
│   ├── auth/                       # Login, register, forgot password, PIN setup
│   ├── onboarding/                 # Profile setup, couple name, avatar
│   ├── records/                    # Transaction list (home tab)
│   ├── transaction/                # Add / edit transaction entry + numpad
│   ├── analysis/                   # Charts, calendar, expense flow
│   ├── budgets/                    # Budget limits and progress
│   ├── accounts/                   # Account management
│   ├── categories/                 # Category management
│   ├── couple/                     # Pairing, combined view, shared budget
│   ├── notes/                      # Notes list and rich text editor
│   └── settings/                   # Theme, profile, couple, notifications
│
├── widget/                         # Glance widget implementations
├── MainActivity.kt
└── IponApp.kt                      # Hilt application class
```

Each feature follows this internal structure:
```
feature_x/
├── data/
│   ├── local/
│   │   ├── XDao.kt
│   │   └── XEntity.kt
│   ├── remote/
│   │   ├── XRemoteDataSource.kt
│   │   └── XDto.kt
│   ├── XRepositoryImpl.kt
│   └── XMapper.kt                  # Entity ↔ Domain model conversion
├── domain/
│   ├── model/
│   │   └── X.kt                    # Domain model — pure Kotlin
│   ├── repository/
│   │   └── XRepository.kt          # Interface only
│   └── usecase/
│       ├── GetXUseCase.kt
│       └── CreateXUseCase.kt
└── presentation/
    ├── XViewModel.kt
    ├── XScreen.kt
    ├── XUiState.kt
    └── components/
```

---

## 4. Data Model

> **Sync columns.** Every synced table also carries `server_rev BIGINT` (server-assigned pull cursor, ADR-0002) in addition to `updated_at` (client-set LWW key, ADR-0001) and `is_deleted` (soft delete, ADR-0010). The local Room mirror additionally has a **`pending_sync` boolean — local only, never sent to Supabase** (push outbox flag, ADR-0002). Columns below omit `server_rev` for brevity; see `supabase/schema.sql` for the authoritative DDL. Design rationale lives in `docs/adr/`.

### users
```
id              UUID        PK (from Supabase Auth)
display_name    TEXT
avatar_url      TEXT        nullable — photo upload is post-V1; V1 shows colored initials/monogram
accent_color    TEXT        hex — used for color-coding in combined view
couple_id       UUID        FK → couples (nullable)
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

### couples
```
id              UUID        PK
couple_name     TEXT        e.g. "PattyWallet"
invite_code     TEXT        UNIQUE — generated on couple creation (gen_invite_code)
user1_id        UUID        FK → users
user2_id        UUID        FK → users (nullable until partner redeems invite)
created_at      TIMESTAMP
updated_at      TIMESTAMP   client-set LWW key (couples is a synced table)
is_deleted      BOOLEAN     soft-deleted on unpair
```
Pairing is done via the `redeem_invite` / `create_couple` RPCs, not direct writes (ADR-0006); unpair via the `unpair` RPC (ADR-0008).

### accounts
```
id              UUID        PK
user_id         UUID        FK → users
name            TEXT        e.g. GCash, Card, Wallet
type            ENUM        CASH | CARD | BANK | EWALLET
opening_balance DECIMAL     current balance is DERIVED (opening + ledger), not stored — ADR-0007
icon            TEXT
color           TEXT
position        INT         sort order
is_archived     BOOLEAN
created_at      TIMESTAMP
updated_at      TIMESTAMP
is_deleted      BOOLEAN     soft delete for sync safety
```

### categories
```
id              UUID        PK
user_id         UUID        FK → users
name            TEXT
type            ENUM        INCOME | EXPENSE
icon            TEXT
color           TEXT
position        INT
is_archived     BOOLEAN
created_at      TIMESTAMP
updated_at      TIMESTAMP
is_deleted      BOOLEAN
```

### transactions
```
id              UUID        PK
user_id         UUID        FK → users
type            ENUM        INCOME | EXPENSE | TRANSFER
amount          DECIMAL
category_id     UUID        FK → categories (nullable for transfers)
account_id      UUID        FK → accounts (source account)
to_account_id   UUID        FK → accounts (nullable, transfer destination only)
note            TEXT
date            TIMESTAMP
is_private      BOOLEAN     hidden from partner in combined view
recurring_rule_id UUID      FK → recurring_rules (nullable)
created_at      TIMESTAMP
updated_at      TIMESTAMP
is_deleted      BOOLEAN
```

### recurring_rules
```
id              UUID        PK
user_id         UUID        FK → users
frequency       ENUM        DAILY | WEEKLY | MONTHLY | CUSTOM
interval        INT         every N units
next_date       DATE
end_date        DATE        nullable = no end date
template        JSONB       stores amount, category_id, account_id, note
created_at      TIMESTAMP
updated_at      TIMESTAMP
is_deleted      BOOLEAN
```

### budgets
```
id              UUID        PK
user_id         UUID        FK → users (null if shared budget)
couple_id       UUID        FK → couples (null if personal budget)
category_id     UUID        FK → categories (null = overall monthly budget)
amount          DECIMAL
year_month      TEXT        e.g. "2026-06"
created_at      TIMESTAMP
updated_at      TIMESTAMP
is_deleted      BOOLEAN
```
Personal budget: `user_id` set, `couple_id` null.
Shared budget: `couple_id` set, `user_id` null.

### partner_debts
```
id              UUID        PK
couple_id       UUID        FK → couples (scoped to the couple — both partners read/write)
borrower_id     UUID        FK → users (the partner who owes)
lender_id       UUID        FK → users (the partner who is owed)
amount          DECIMAL     original amount at debt creation
description     TEXT        what the debt is for
created_at      TIMESTAMP
updated_at      TIMESTAMP
is_deleted      BOOLEAN     soft-deleted on unpair (same as shared budgets)
```
Remaining balance is derived (amount − sum of non-deleted payments); never stored.
Only enabled in the UI when the user is paired. Either partner may create or settle a debt.

### partner_debt_payments
```
id         UUID        PK
debt_id    UUID        FK → partner_debts
amount     DECIMAL     this instalment's payment amount
note       TEXT        optional description
date       TIMESTAMP   when the payment was made
created_at TIMESTAMP
updated_at TIMESTAMP
is_deleted BOOLEAN
```

### notes
```
id              UUID        PK
user_id         UUID        FK → users
title           TEXT
content         JSONB       rich text delta format
is_shared       BOOLEAN
couple_id       UUID        FK → couples (nullable, populated when shared)
created_at      TIMESTAMP
updated_at      TIMESTAMP
is_deleted      BOOLEAN
```

### note_images
```
id              UUID        PK
note_id         UUID        FK → notes
storage_url     TEXT        Supabase Storage bucket URL
position        INT
created_at      TIMESTAMP
updated_at      TIMESTAMP   synced like everything else
is_deleted      BOOLEAN     soft delete so image removal propagates
```
Notes un-sharing sets `is_shared = false` but **retains `couple_id`** so the un-share reaches the partner's redacting view (ADR-0005). Partner reads of transactions/accounts/categories/notes/note_images go through redacting views, never the base tables (ADR-0005).

---

## 5. Authentication Flow

**Email + password only in v1.**

**Register:**
1. User enters display name, email, password
2. Supabase Auth creates account, sends verification email
3. On verification, app creates a row in the `users` table
4. User proceeds to onboarding (profile setup, couple name)

**Login:**
1. User enters email + password
2. Supabase Auth returns session (access token + refresh token) — rejected until the email is verified
3. Session stored in DataStore
4. App navigates to home (Records tab)

**PIN / Biometric (local only):**
- PIN is hashed and stored in EncryptedDataStore
- Biometric uses AndroidX Biometric library
- Both are a local app lock only — they do not replace the Supabase session
- Challenged on app resume from background after 1 minute of inactivity

**Session management:**
- Supabase SDK auto-refreshes the session token
- If refresh fails (e.g., account revoked), user is sent back to login

---

## 6. Offline-First Sync Strategy

**Room is the single source of truth. Always.**

```
User action
  → Write to Room immediately
  → UI updates from Room (via Flow)
  → WorkManager pushes to Supabase in background
```

**On any write:**
- `updated_at = max(now() + clockOffset, existing_updated_at + 1ms)` — offset-corrected toward server time, monotonic so a backward clock jump can't lose to the row's own prior version (ADR-0001)
- `pending_sync = true` (local-only outbox flag)
- Deletes set `is_deleted = true` and update `updated_at` (soft delete — never hard delete locally)

**Sync triggers:**
- App comes to foreground
- Network reconnects (NetworkCallback)
- User pulls to refresh on Records or Notes screens
- Single-flight: triggers coalesce via WorkManager unique work (no overlapping syncs)

**Sync logic** (ADR-0002, ADR-0009):
- **Push (outbox):** send all rows where `pending_sync = true`; clear the flag per row as its upsert is acked. No timestamp math — the dirty flag, not `updated_at`, selects what to push.
- **Pull (cursor):** for each table, fetch rows where `server_rev > cursor`, ordered by `server_rev`; advance the per-table cursor (in DataStore) to the max `server_rev` received, only after the batch commits to Room. `server_rev` is a server-assigned sequence reflecting *receipt order*, so a partner's late-arriving old edit is still pulled (fixes the "late arrival below the high-water mark" loss).
- **Ordering:** process tables parent→child both directions (users → couples → accounts → categories → recurring_rules → transactions → budgets → notes → note_images); upserts are idempotent by `id`, so an interrupted sync just resumes.
- **Conflict resolution:** row-level last-write-wins by `updated_at` (ADR-0003). The one lossy merge case (local dirty + remote newer) discards local edits everywhere *except shared notes*, which fork into a **conflict copy** instead of losing data.
- **Partner data (combined view):** pulled from the redacting views (`partner_transactions`, etc.); a row arriving flagged private/deleted (note: unshared) means **purge the local copy** (ADR-0005). On unpair, the local `users` row going `couple_id = null` triggers a bulk purge of all replicated non-owned rows (ADR-0008).
- **Balance:** never synced — derived locally from `opening_balance` + the ledger (ADR-0007).
- **Tombstones:** kept indefinitely; a fresh device (cursor 0) pulls only `is_deleted = false` (ADR-0010).

**Sync status** is a `StateFlow` in `SyncManager`, shown subtly in the UI (small indicator, not intrusive).

> Full rationale for every decision above is in `docs/adr/0001`–`0011`; domain glossary in `CONTEXT.md`.

---

## 7. Navigation Structure

**Bottom navigation — 5 tabs (matching MyMoney layout):**
```
Records | Analysis | Budgets | Accounts | Categories
```

**Outside the bottom nav:**
```
Auth graph        → Login → Register → Forgot password
Onboarding graph  → Profile setup → Couple setup
Notes             → Notes list → Note editor
Couple view       → Combined view → Shared budget
Settings          → Theme / Profile / Couple / Notifications
Transaction entry → Add / Edit (bottom sheet modal over any tab)
```

---

## 8. Theme Architecture

**Model: Palette × Mode.** Users pick a palette (6 options) and light/dark mode independently. Both stored in DataStore; `IponTheme` reads them at the app root via `CompositionLocal`.

**Palettes** (seed = M3 light primary; full `ColorScheme` derived by M3 tonal palette generation):

| Palette | Seed |
|---|---|
| Rose | `#C2647A` |
| Mauve | `#9B6B7A` |
| Lavender | `#8B7BB5` |
| Peach | `#C47A5A` |
| Sage | `#6B8F71` |
| Mocha | `#8B6F5A` |

**Personalize screen** (Settings → "Personalize"): `LazyVerticalGrid` of palette swatches + light/dark toggle. Tapping a swatch previews live on the screen itself (local VM state); Save/Apply persists to DataStore.

**Couple attribution color is separate from theme palette.** Fixed blue vs pink in the combined view, chosen per-partner during the couple pairing flow, stored as `accent_color` on the `users` row. See ADR-0014.

---

## 9. Android Configuration

| Setting | Value |
|---|---|
| minSdk | 26 (Android 8.0) — covers ~95%+ of active devices |
| compileSdk | 37 (android-37.0 stable platform) |
| targetSdk | 35 (Android 15) |
| Package name | com.iponlove.app |
| Language | Kotlin |
| Build toolchain | AGP 9.2.1 / Gradle 9.6 / Kotlin 2.2.10 (built-in via AGP) / JDK 21 |
| Supabase region | ap-southeast-1 (Singapore — closest to PH) |

---

## 10. Post-V1 Enhancements

Not built in v1. Architecture must not block these. Reconciled 2026-07-05 against `docs/build/project-build-progress.md` and `PRD.md` (previously out of sync — see that doc's "Post-V1 Horizon" list, now 13 items). Target quarters aren't duplicated here — `project-build-progress.md`'s Horizon list is the single source of truth for those.

| Feature | Note |
|---|---|
| Google Sign-In | Supabase OAuth + Android Credential Manager; needs Google Cloud Console setup |
| Facebook Login | Supabase OAuth + Facebook SDK; needs Facebook Developer App setup |
| AI companion | Hybrid: app-funded capped tier (cheap model) by default + opt-in BYOK (key stored in EncryptedDataStore) for unlimited use; new `feature/ai` module |
| Password vault | New `feature/vault` module; SQLCipher or EncryptedDataStore |
| Voice recording storage | New `feature/recordings` module; Supabase Storage for upload |
| iOS | Evaluate Kotlin Multiplatform — domain layer is pure Kotlin already, head start exists |
| CSV / PDF export | Power user feature, data already structured for it |
| Custom fonts | Typography customization beyond built-in color themes; category/account icon picking already shipped in V1.3 |
| Profile & couple photo upload | Avatar/banner images via Supabase Storage |
| Change password / change email | New Settings screen addition; email change likely needs Supabase Auth's update-user + reverification flow |
| Delete my account | New RPC per ADR-0006/0008 (couple-ops-are-RPCs-only); needs a self-service vs. support-mediated decision |
| Login rate limiting / lockout | Decide whether Supabase's own server-side rate limiting already covers this or a client-side cooldown is also needed |
| "Restart fresh" (reset finances) | `ResetFinancesUseCase` — owned-rows-only bulk soft-delete across transactions/recurring rules/budgets/goal contributions in a single Room `@Transaction`, then an interactive push; per ADR-0037 |
