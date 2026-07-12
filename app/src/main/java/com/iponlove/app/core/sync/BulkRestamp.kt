package com.iponlove.app.core.sync

import java.time.Instant

/**
 * Applies [transform] to every row [fetch] returns, giving each its own monotonic `updated_at`
 * floor (ADR-0001, same invariant as any single-row write) before [upsert]ing it back. The
 * shared shape for any owned-row bulk mutation — Reset finances (ADR-0037) soft-deletes
 * transactions and zeroes account opening balances through this; a future account-deletion
 * cascade can reuse it with a tombstoning transform. Caller owns the transaction boundary
 * (e.g. one [LocalTransactionRunner.run]) and the push trigger.
 */
suspend fun <T : SyncMeta> bulkRestamp(
    clock: SyncClock,
    fetch: suspend () -> List<T>,
    transform: (T, Instant) -> T,
    upsert: suspend (T) -> Unit,
) {
    fetch().forEach { row -> upsert(transform(row, clock.stamp(row.updatedAt))) }
}
