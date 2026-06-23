# Architecture Document — Ipon, Love

**Version:** 1.0 (Draft)
**Date:** June 2026
**Companion doc:** PRD.md

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

### users
```
id              UUID        PK (from Supabase Auth)
display_name    TEXT
avatar_url      TEXT
accent_color    TEXT        hex — used for color-coding in combined view
couple_id       UUID        FK → couples (nullable)
created_at      TIMESTAMP
updated_at      TIMESTAMP
```

### couples
```
id              UUID        PK
couple_name     TEXT        e.g. "PattyWallet"
invite_code     TEXT        UNIQUE — generated on couple creation
user1_id        UUID        FK → users
user2_id        UUID        FK → users (nullable until partner accepts)
created_at      TIMESTAMP
```

### accounts
```
id              UUID        PK
user_id         UUID        FK → users
name            TEXT        e.g. GCash, Card, Wallet
type            ENUM        CASH | CARD | BANK | EWALLET
balance         DECIMAL
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
```

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
2. Supabase Auth returns session (access token + refresh token)
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
- `updated_at` is set to `now()`
- Deletes set `is_deleted = true` and update `updated_at` (soft delete — never hard delete locally)

**Sync triggers:**
- App comes to foreground
- Network reconnects (NetworkCallback)
- User pulls to refresh on Records or Notes screens

**Sync logic:**
- Push: all local records where `updated_at > last_sync_at`
- Pull: all remote records where `updated_at > last_sync_at`
- Conflict resolution: last-write-wins by `updated_at`
- `last_sync_at` stored per user in DataStore

**Sync status** is a `StateFlow` in `SyncManager`, shown subtly in the UI (small indicator, not intrusive).

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

- Each theme is a data object implementing an `AppTheme` interface
- `AppTheme` defines: `ColorScheme`, `Typography`, icon set variant
- Active theme stored in DataStore, applied at the app root via `IponTheme { }` wrapper
- A Compose `CompositionLocal` propagates the theme down the tree
- Adding a new theme = add one new object. No structural changes needed.

---

## 9. Android Configuration

| Setting | Value |
|---|---|
| minSdk | 26 (Android 8.0) — covers ~95%+ of active devices |
| targetSdk | 35 (Android 15) |
| Package name | com.iponlove.app |
| Language | Kotlin |
| Supabase region | ap-southeast-1 (Singapore — closest to PH) |

---

## 10. Post-V1 Enhancements

Not built in v1. Architecture must not block these.

| Feature | Note |
|---|---|
| Google Sign-In | Supabase OAuth + Android Credential Manager; needs Google Cloud Console setup |
| Facebook Login | Supabase OAuth + Facebook SDK; needs Facebook Developer App setup |
| AI companion | User provides own API key; stored in EncryptedDataStore; new `feature/ai` module |
| Receipt photo on transactions | `photo_url` column on transactions; Supabase Storage already available |
| Password vault | New `feature/vault` module; SQLCipher or EncryptedDataStore |
| Voice recording storage | New `feature/recordings` module; Supabase Storage for upload |
| iOS | Evaluate Kotlin Multiplatform — domain layer is pure Kotlin already, head start exists |
| CSV / PDF export | Power user feature, data already structured for it |
