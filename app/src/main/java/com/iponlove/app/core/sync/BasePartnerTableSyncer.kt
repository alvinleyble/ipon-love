package com.iponlove.app.core.sync

/**
 * The pull-only algorithm for a partner redacting view (ADR-0005).
 *
 * Partner rows are read-only on this device:
 *  - [push] is a no-op — the base-table RLS policy prevents writing another user's rows.
 *  - [pull] fetches from the partner view, which: (a) excludes the caller's own rows
 *    server-side (`WHERE user_id <> auth.uid()`), (b) nulls out content columns when a
 *    row is private/deleted/unshared so the transition still crosses the wire (ADR-0005).
 *
 * For each pulled row:
 *  - [shouldPurge] true  → hard-delete from Room (content was redacted, privacy guarantee).
 *  - [shouldPurge] false → upsert into Room (non-redacted visible partner row).
 *
 * No conflict resolution: partner rows are never dirty locally, so LWW is irrelevant.
 * Cursor bookkeeping and resumability are identical to [BaseTableSyncer].
 */
abstract class BasePartnerTableSyncer<R : SyncMeta>(
    final override val table: SyncTable,
    private val cursors: SyncCursorStore,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : TableSyncer {

    /** Fetch up to [pageSize] rows from the partner view with `server_rev > cursor`. */
    protected abstract suspend fun remotePullPartner(cursor: Long, limit: Int): List<R>

    /**
     * True when [row] is hidden from the partner (private, deleted, or unshared note);
     * the view always returns such rows so the removal propagates — but with nulled content.
     */
    protected abstract fun shouldPurge(row: R): Boolean

    /** Hard-delete [id] from Room (partner rows have no tombstone lifecycle of their own). */
    protected abstract suspend fun hardDelete(id: String)

    /** Upsert the visible partner [rows] into Room in a single transaction. */
    protected abstract suspend fun applyPullBatch(rows: List<R>)

    // Partner rows are read-only on this device, so a push never sends anything (and never
    // rings the bell): always false.
    final override suspend fun push() = false

    final override suspend fun pull() {
        var cursor = cursors.cursor(table)
        while (true) {
            val batch = remotePullPartner(cursor, pageSize)
            if (batch.isEmpty()) break

            val toUpsert = ArrayList<R>(batch.size)
            for (row in batch) {
                if (shouldPurge(row)) hardDelete(row.id)
                else toUpsert += row
            }
            if (toUpsert.isNotEmpty()) applyPullBatch(toUpsert)

            val maxRev = batch.mapNotNull { it.serverRev }.maxOrNull()
            if (maxRev != null && maxRev > cursor) {
                cursor = maxRev
                cursors.setCursor(table, cursor)
            }
            if (batch.size < pageSize) break
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 500
    }
}
