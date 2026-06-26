# Display name captured at registration via Supabase auth metadata

**Context.** Users had no display name. The `users.display_name` column and its full data path (entity, DTO, domain model, mappers, sync) already existed, but nothing ever *wrote* it — `EnsureCurrentUserRowUseCase` seeded new rows with `displayName = null`. This surfaced when the partner needed a name: the combined view and shared-note attribution had nothing to show but a generic "Partner".

The complication: **email confirmation is ON before ship**, so at registration there is no session and no `users` row. The row is created on *first authenticated login* (post email-confirm) via `EnsureCurrentUserRowUseCase` (ADR-0013 — no DB trigger creates it). A name typed on the registration form must therefore survive the gap between sign-up and first login, possibly on a different device.

**Decisions.**

**Name is captured at registration, required, and carried via Supabase auth `user_metadata`.** The registration form gains a required, trimmed, non-blank, ≤50-char name field (validated by a new `AuthCredentials.validateName()` alongside the email/password checks). `signUp` passes `data = { display_name: name }` into `signUpWith(Email)`, so the name lives on the Supabase auth account from sign-up.

**The name is seeded into the row when it is created, not before.** On first login `EnsureCurrentUserRowUseCase` reads the name from the live session and passes it to `ensureLocalRow(userId, displayName)`, which stamps it on the new dirty stub. The row then pushes normally (ADR-0013 ordering). This respects ADR-0013 — the use case still creates the row; no DB trigger is involved. The reinstall path (server row already exists) adopts the server row unchanged, so a name set earlier is never clobbered.

**Session metadata is surfaced through `CurrentUserProvider`.** `CurrentUserProvider` gains `displayName(): String?` reading `session.user.userMetadata["display_name"]`, mirroring how it already provides `userId()`. This keeps session-infrastructure reads out of the otherwise pure Room+clock repository and stays unit-testable by mocking the provider.

**Editing lives in a new Settings → Profile submodule.** A "Profile" `ListItem` row is added to the Personalize screen alongside the existing "Security" row (same pattern), opening a new `ProfileScreen`. It holds: the **display name** (editable; new `UpdateDisplayNameUseCase` mirrors `UpdateAccentColorUseCase` — stamps `pending_sync`, syncs via the existing `UserDto` path with no new sync plumbing); the **accent color** (the couple attribution color, previously only settable during the pairing flow — surfaced here so a paired user can change it anytime; reuses the slice-L `ColorPickerDialog`; hidden when single); and the account **email**, shown read-only for "which account am I in" clarity.

**Null name falls back to a generic label, never the email.** Where a name renders (partner-note badge, combined-view attribution, color picker), a null `displayName` falls back to "Partner" for the partner and "You" for self — never the login email (a partner must not see your email). With registration-required names this is purely a defensive default for sync-timing (the partner's row may not have synced yet); no migration or forced re-prompt for null-name accounts is needed, as the app is pre-ship with no live userbase.

**Rejected: local DataStore stash of the registration name.** Save the name locally at sign-up, apply on first login. Rejected as fragile — it breaks if the user confirms/logs in on a different device or clears app data between sign-up and confirmation.

**Rejected: a mandatory onboarding "What's your name?" screen.** Gate the app on first login until a name is entered. It always captures a name on the logging-in device and needs no metadata plumbing, but it adds a new nav gate and contradicts the "capture at registration" intent. Auth metadata achieves the same robustness without the extra screen; an onboarding gate remains available later as a safety net if a null-metadata path ever appears.
