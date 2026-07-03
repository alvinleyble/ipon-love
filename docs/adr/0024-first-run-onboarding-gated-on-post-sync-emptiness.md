# First-run onboarding graph gated on post-sync emptiness

New users get a first-run onboarding graph — Welcome/value-prop → pair-or-solo → starter-template picker → home — introduced because none existed (`MainActivity` went straight from authenticated to `IponApp`). Pairing precedes templates so the app leads with its couples identity, and a freshly-paired user is more invested by the template step.

The new-user signal is **not** local Room emptiness and **not** a device flag — it is *"owned categories and accounts are both empty after the first sync has successfully completed."* Keying on raw local emptiness would re-onboard and **seed duplicate starter categories** for a returning user who reinstalls the APK or signs in on a second device (fresh Room, then sync pulls their real rows in alongside the seeded ones, distinct IDs, unmergeable).

## Consequences

- A failed/offline first sync (`sync()` is `runCatching`) shows nothing — the new-user decision defers to the next successful-sync launch. Never seed on an unknown sync result.
- An `onboardingDone` DataStore flag is **only** a re-prompt suppressor for this device, not the new-user signal.
- The pairing step is an explicit fork — *"Invite my partner"* (`create_couple`) vs *"I have a code"* (`redeem_invite`, ADR-0006/0008) — so two partners both onboarding don't each create a separate couple. Solo is a first-class exit; skipping pairing here is caught later by the unpaired home-screen pairing card.
- Starter seeding inserts personal `CategoryEntity`/`AccountEntity` rows (`pending_sync=true`), is idempotent, and pushes in FK order (ADR-0009).

## Addendum (2026-07-03): onboarding flags reclassified per-account, not per-device

Originally the `onboardingDone`/`pairingCardDismissed` DataStore flags were deliberately excluded from `LocalDataWiper` (per-device, like theme/app-lock prefs) — see git history on `OnboardingModule.kt`. Manual multi-account testing on one device (three different accounts signed in sequentially on the same emulator) surfaced the consequence: once any account completed onboarding, every subsequent *different* account signing in on that device silently skipped the graph — including the starter-template seeding step — landing in an empty app with no accounts/categories and no explanation.

Reclassified as per-account: `LocalDataWiper.wipe()` now also calls `OnboardingRepository.reset()`, alongside Room/cursors/nav config. A device shared across accounts (family tablet, QA/dev device) now re-runs onboarding — including seeding — for each newly-signed-in account, same as the very first account did. The post-sync-emptiness signal (this ADR's core decision) is unchanged: the flag remains a pure re-prompt suppressor, just scoped to "this account has completed onboarding" rather than "this device has, ever, for anyone."
