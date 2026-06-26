# Live sync: push-on-write + Realtime broadcast "bell" + pull-on-ping

**Context.** V1 sync is manual-only: a write sets `pending_sync = true` but nothing reaches the server until the next full `SyncEngine.sync()` (foreground resume, pull-to-refresh, or the WorkManager periodic run). Two distinct pains result:

- **Own writes lag the server** — Room updates instantly (offline-first), but the row isn't pushed until a full sync, which walks all ~15 tables push-then-pull sequentially.
- **Partner data is never live** — a partner's change is invisible until the local user manually pulls or WorkManager fires. The couple never feels in sync.

The hard constraint shaping the solution: **partner data is exposed through redacting views with RLS (ADR-0004/0005), not base tables.** So the obvious Realtime approach — subscribing to `postgres_changes` on the partner's base tables — is unusable: the partner has no SELECT on your base rows, and relaxing RLS to permit it would leak private/unshared rows in the change payload, defeating the redaction. The two problems are independent and were solved with independent mechanisms.

**Decisions.**

**Two new narrow `SyncEngine` entry points alongside the existing full `sync()`.**
- `pushOnly()` — runs the push half (`for (syncer in ordered) syncer.push()`) in FK order, no pull.
- `pullOnly()` — runs the pull half through the redacting-view syncers, no push.
- The full `sync()` remains the foreground-resume / pull-to-refresh / WorkManager path. All three share the engine's single-flight `Mutex`, so any overlap coalesces (a ping during a full sync is a no-op).

**Own writes: debounced `pushOnly()`.** A repository write signals a shared dirty trigger; a coalescing collector waits a short debounce (~1.5–2 s, so rapid edits batch) then runs `pushOnly()`. Push-all-dirty in FK order — never single-table — because a brand-new account and its transaction created in one session must push parent-before-child. Empty tables early-return in `push()` (`dirtyRows().isEmpty()`), so the loop is cheap when little is dirty.

**Partner data: Realtime as a content-less notification "bell," not a data channel.** Each couple has a private Realtime **Broadcast** channel `couple:{coupleId}`. After a push that *actually sent rows*, the writer broadcasts a tiny "changed" ping carrying **no row data**. The partner, subscribed to the channel, reacts by running `pullOnly()` — all real data still flows through the RLS-protected redacting-view pull. Because the ping carries nothing, ADR-0005 redaction is never bypassed.

**Receive action is pull-only, debounced, mutex-coalesced.** A ping means "partner changed something," so only a pull is needed (not a push of my own rows). A burst of partner pings collapses into one pull via a ~1 s debounce; if a full sync is already in flight, the ping is dropped by the single-flight mutex.

**No ping-pong loop.** A pull writes rows with `pending_sync = false`, so receiving a ping → `pullOnly()` creates no dirty rows → triggers no push → emits no broadcast. Self-echo is additionally disabled (`receiveOwnBroadcasts = false`) so only the *partner* reacts to a ping.

**Private channel, RLS-authorized.** The channel is private; access is gated by an RLS policy on `realtime.messages` keyed to `auth_couple_id()` (the existing schema helper), so only the two couple members can subscribe or broadcast. Even though pings carry no data, this prevents anyone who learns a `coupleId` from spamming pull-triggers at a couple or detecting that a couple is active — matching the security-first posture of the redacting-views design.

**Subscription lifecycle: foreground + paired only, owned by a `core` `CoupleChannelManager`.** A `@Singleton CoupleChannelManager` (in `core/`, not feature or activity code — per the "sharing logic stays generic, lives in core" scalability principle) `combine`s two flows: a foreground signal fed by `MainActivity`'s existing process-lifecycle observer, and `ObservePairingStateUseCase` (yielding `coupleId` or null). It subscribes to `couple:{coupleId}` only when *both* foregrounded and paired; unsubscribes when either drops; swaps channels when `coupleId` changes (re-pair). Single users never subscribe. While backgrounded, the existing WorkManager periodic sync (ADR-0012) is the safety net.

**The sequential table loop is left unoptimized.** Each `sync()`/`pullOnly()` still walks ~15 tables back-to-back, even when most are empty cursor-filtered SELECTs. The perceived slowness was *triggering*, not the loop; the loop runs in the background while Room already shows local data. Parallelizing pulls or adding a "what changed" digest RPC was rejected for this slice as added complexity for unmeasured gain — revisit only if it demonstrably lags.

**Rejected: `postgres_changes` on base tables.** The standard Realtime approach. Rejected because it requires partners to SELECT each other's base tables, which breaks ADR-0005 — private notes and unshared transactions would leak in the change payload.

**Rejected: foreground polling every ~20 s.** No new dependency and respects the views, but not genuinely live (up to 20 s lag) and burns round-trips. Underdelivers on the "live" goal.

**Rejected: always-on (background) Realtime socket.** True background push, but a held socket drains battery and Android Doze kills it anyway. Not worth it for couples finance, where WorkManager covers the backgrounded case.
