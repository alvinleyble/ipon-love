# First-run onboarding graph gated on post-sync emptiness

New users get a first-run onboarding graph — Welcome/value-prop → pair-or-solo → starter-template picker → home — introduced because none existed (`MainActivity` went straight from authenticated to `IponApp`). Pairing precedes templates so the app leads with its couples identity, and a freshly-paired user is more invested by the template step.

The new-user signal is **not** local Room emptiness and **not** a device flag — it is *"owned categories and accounts are both empty after the first sync has successfully completed."* Keying on raw local emptiness would re-onboard and **seed duplicate starter categories** for a returning user who reinstalls the APK or signs in on a second device (fresh Room, then sync pulls their real rows in alongside the seeded ones, distinct IDs, unmergeable).

## Consequences

- A failed/offline first sync (`sync()` is `runCatching`) shows nothing — the new-user decision defers to the next successful-sync launch. Never seed on an unknown sync result.
- An `onboardingDone` DataStore flag is **only** a re-prompt suppressor for this device, not the new-user signal.
- The pairing step is an explicit fork — *"Invite my partner"* (`create_couple`) vs *"I have a code"* (`redeem_invite`, ADR-0006/0008) — so two partners both onboarding don't each create a separate couple. Solo is a first-class exit; skipping pairing here is caught later by the unpaired home-screen pairing card.
- Starter seeding inserts personal `CategoryEntity`/`AccountEntity` rows (`pending_sync=true`), is idempotent, and pushes in FK order (ADR-0009).
