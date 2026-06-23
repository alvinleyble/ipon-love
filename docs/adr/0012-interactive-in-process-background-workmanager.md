# Interactive sync runs in-process; WorkManager owns background retry

**Context.** Sync has both user-facing triggers (pull-to-refresh, app-to-foreground) and reliability triggers (retry after failure, network-reconnect, periodic safety net). WorkManager is deferrable by design — even expedited unique work can lag seconds-to-minutes under quota/Doze/batching — which is wrong for the triggers a user is actively watching (a pull-to-refresh spinner, or foregrounding to see if a partner's payment landed).

**Decision.** Split by trigger type over one shared `SyncManager.sync()` core. Interactive triggers call `sync()` directly from a coroutine (in-process), so the spinner maps to the real round-trip and results land immediately. WorkManager owns the deferrable/reliable paths: retry-with-backoff after a failed interactive sync, the network-reconnect trigger (constraint-gated), and any periodic sweep — leveraging WorkManager for *survival* (process death, guaranteed retry), not immediacy. Single-flight is enforced on both sides: an in-process Mutex plus WorkManager unique work, so the two paths never overlap.

**Rejected:** routing everything through WorkManager (uniformly laggy for interactive use) and everything in-process (loses guaranteed background retry across process death). Corrects ARCHITECTURE.md §6's "WorkManager pushes in background" to this split.
