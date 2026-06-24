package com.iponlove.app.core.sync

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Produces the client-set `updated_at` LWW key, hardened per ADR-0001:
 *
 *  1. **Offset correction** — the device clock can be skewed against the server; a
 *     persisted offset (millis, server − local, captured on each sync) is added so a
 *     fast/slow phone self-corrects toward server time and can't perpetually win or
 *     lose shared-row conflicts.
 *  2. **Local monotonicity** — `updated_at = max(now + offset, prev + 1ms)`, so a
 *     backward wall-clock jump can never make a genuinely newer edit lose to the
 *     row's own prior version.
 *
 * Deliberately keeps `updated_at` client-authoritative — no server trigger overrides
 * it (that would clobber conflict resolution on push, ADR-0001/ADR-0002).
 *
 * The offset is held in memory for a synchronous, allocation-light write hot path;
 * persistence (restore on launch, save after sync) is a thin DataStore concern that
 * reads/writes [offsetMillis] — see `ClockOffsetStore`.
 *
 * @param now source of the raw device wall clock; defaults to [Instant.now].
 */
class SyncClock(
    private val now: () -> Instant = Instant::now,
    initialOffsetMillis: Long = 0L,
) {
    private val offset = AtomicLong(initialOffsetMillis)

    /** Current server-minus-local offset in millis; read/written by the persistence layer. */
    var offsetMillis: Long
        get() = offset.get()
        set(value) = offset.set(value)

    /**
     * Stamp a write. Pass the row's existing `updated_at` (or null for a brand-new row)
     * to guarantee strict forward monotonicity for that row.
     */
    fun stamp(previousUpdatedAt: Instant? = null): Instant {
        val corrected = now().plusMillis(offset.get())
        val floor = previousUpdatedAt?.plusMillis(1)
        return if (floor != null && floor.isAfter(corrected)) floor else corrected
    }

    /**
     * Recompute the offset from a fresh server timestamp observed during sync. Capture
     * [localNow] as close as possible to when [serverNow] was read so round-trip latency
     * doesn't pollute the estimate.
     */
    fun recordServerTime(serverNow: Instant, localNow: Instant = now()) {
        offset.set(serverNow.toEpochMilli() - localNow.toEpochMilli())
    }
}
