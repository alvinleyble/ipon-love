# App lock renders as an overlay, and the PIN fallback is never removed

The lock was a branch-swap in `MainActivity` (`if (isLocked) LockScreen else IponApp`), which tore the entire `NavHost` out of composition on every lock; on unlock the `NavController` and all `hiltViewModel()` stores rebuilt from the start destination, silently discarding any in-progress work. This — not the add-transaction dialog being a dialog — is the real cause of "my half-written transaction vanished when I came back" (the reported ~30s being the lock grace period, not process death). We now render `LockScreen` as an opaque, full-screen overlay above an **always-composed** `IponApp`, so navigation state and ViewModels survive the lock app-wide. Transaction (and other) drafts are *additionally* backed by `SavedStateHandle` so they survive true process death too — the overlay alone does not.

## Considered Options

- **Convert add-transaction to a route** (the original plan's fix for the lost draft): rejected as insufficient — a NavHost destination gives zero protection against either the lock teardown or process death. The route conversion proceeds, but justified by UX (room for the reorderable picker), not durability.
- **`SavedStateHandle` only**: covers process death but still loses every *other* screen's state on each lock. The overlay is what makes state preservation app-wide.

## Consequences

- `AppLockViewModel.pin` must be cleared on lock-engage (and on successful unlock), not only on failed verify — it was leaking the previous PIN across locks, showing four filled dots and forcing the user to delete before typing (the item-8 stale-fill bug).
- When biometric is enabled the lock opens in **biometric mode** (auto-prompt + a persistent "Use PIN instead"), but *every* non-success biometric callback — negative button, fail, `ERROR_LOCKOUT`/`ERROR_LOCKOUT_PERMANENT`, hardware error — routes to the PIN pad. The PIN may be visually deferred but **never removed**; otherwise a biometric lockout would lock a user out of their own app. The current `BiometricPrompt` callback handles only `onAuthenticationSucceeded`, so this fallback wiring is the bulk of the work, not an add-on.
