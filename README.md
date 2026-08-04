# Love, Ipon

> A personal finance app for couples in the Philippines.

Track income and expenses, manage budgets, and stay financially aligned with your partner — offline-first, with Supabase background sync (nudged live by a realtime partner bell).

---

## Features

**Personal**
- Expense and income tracking across multiple accounts (cash, bank, e-wallet, credit card)
- Monthly budgets per category with progress tracking
- Spending analysis — donut chart, cumulative expense flow, daily calendar view
- Receipt photo attachments on transactions
- Recurring transactions
- Rich-text notes with images and checklists

**Couples**
- Combined spending view with colour-coded attribution
- Shared couple budget
- Partner Debt Tracker — log IOUs, record settlements, auto-net opposing debts
- Shared notes

**App**
- Offline-first (Room local DB, Supabase background sync)
- PIN + biometric app lock
- Home screen balance widget + quick-add shortcut widget
- Multiple colour themes, light and dark mode

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture (feature-based) |
| Local DB | Room (offline-first source of truth) |
| Backend | Supabase (Auth · Postgres · Storage · Realtime) |
| DI | Hilt |
| Async | Coroutines + StateFlow |
| Background sync | WorkManager |
| Preferences | Jetpack DataStore |
| Widget | Jetpack Glance |
| Image loading | Coil |
| Rich text | Compose Rich Editor |
| Build | Gradle Kotlin DSL + Version Catalogs |

---

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer (JDK 21 / JBR bundled)
- Android SDK 37

### Setup

1. Clone the repo
2. Create `local.properties` in the project root (already gitignored):

```properties
sdk.dir=/path/to/your/Android/sdk

# Staging Supabase project
STAGING_SUPABASE_URL=https://your-project.supabase.co
STAGING_SUPABASE_ANON_KEY=your_anon_key

# Prod (leave blank until prod project is created)
PROD_SUPABASE_URL=
PROD_SUPABASE_ANON_KEY=

# Release signing — only needed to build a release APK/AAB. Debug builds, lint and the
# JVM unit tests run fine without these; a release build fails loudly if any is missing.
RELEASE_STORE_FILE=
RELEASE_STORE_PASSWORD=
RELEASE_KEY_ALIAS=
RELEASE_KEY_PASSWORD=
```

3. Build and install:

```bash
./gradlew assembleStagingDebug
adb install app/build/outputs/apk/staging/debug/app-staging-debug.apk
```

### Build commands

```bash
# Debug APK
./gradlew assembleStagingDebug

# Release AAB (Play Store upload)
./gradlew bundleStagingRelease

# Unit tests (JVM, no emulator needed)
./gradlew testStagingDebugUnitTest

# Lint
./gradlew lintDebug

# Install on connected device
./gradlew installStagingDebug
```

---

## Architecture

Feature-based Clean Architecture. Each feature is a self-contained vertical slice:

```
feature_x/
  data/
    local/          → Room Entity + DAO
    remote/         → Supabase DTO + RemoteSource
    sync/           → TableSyncer (push dirty rows, pull by server_rev cursor)
    XMapper.kt
    XRepositoryImpl.kt
  domain/
    model/          → Pure Kotlin domain model
    repository/     → Repository interface (no Android imports)
    usecase/        → UseCases (one public method each)
  presentation/
    XViewModel.kt   → StateFlow<XUiState> only
    XScreen.kt      → Composable, no business logic
    XUiState.kt
    components/
```

**Key conventions:**
- Money amounts use `BigDecimal` — never `Double` or `Long`
- Soft deletes only (`is_deleted = true`) — no hard deletes, ever
- Every write sets `updated_at` (monotonic LWW clock) and `pending_sync = true`
- Sync order (FK root → leaf): `users → couples → accounts → categories → recurring_rules → transactions → budgets → notes → note_images`

---

## Supabase

- Region: ap-southeast-1 (Singapore)
- Auth: email + password (email verification required)
- Storage: note image attachments + receipt photos
- Realtime: Broadcast channel for live partner sync (bell-only, no row data)

---

## Testing

```bash
# All JVM unit tests (~420 tests, seconds-fast, no emulator)
./gradlew testStagingDebugUnitTest
```

Test coverage targets: sync / conflict resolution, money math, budget calculations, mappers, UseCases. UI verified by running the app, not unit tested.

Stack: JUnit + Truth + Turbine + MockK + kotlinx-coroutines-test

---

## License

Private — all rights reserved. © 2026 Alvin Leyble
