package com.iponlove.app.core.sync

/**
 * The reusable push/pull algorithm for one table, parameterised by its entity type [R].
 *
 * Subclasses supply the table-specific I/O (Room DAO calls and Supabase calls) by
 * overriding the abstract members; all sync bookkeeping — dirty-flag push selection,
 * cursor pull, conflict resolution, idempotent apply, and post-commit cursor advance —
 * lives here so every feature gets it identically (ADR-0002, ADR-0003, ADR-0009).
 *
 * Contract notes for implementers:
 *  - [remotePull] returns rows already in entity shape ([R]), each carrying the
 *    server-assigned `serverRev` and `pendingSync = false`. The entity↔DTO mapping
 *    (which strips `pending_sync` off the wire) is the subclass's job.
 *  - [remotePush] / [applyPullBatch] must be idempotent by `id` (upsert), so a redo
 *    after a mid-batch failure is a no-op (ADR-0009).
 *  - [applyPullBatch] must commit in a single Room transaction; the cursor is advanced
 *    only after it returns.
 *
 * @param pageSize max rows fetched per pull round-trip; pull loops until a short page,
 *                 advancing the cursor each page so a dropped connection resumes mid-table.
 */
abstract class BaseTableSyncer<R : SyncMeta>(
    final override val table: SyncTable,
    private val cursors: SyncCursorStore,
    private val resolver: ConflictResolver,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : TableSyncer {

    // ---- table-specific I/O (subclass supplies) ----

    /** Local rows with `pending_sync = true`. */
    protected abstract suspend fun dirtyRows(): List<R>

    /** Clear `pending_sync` for the given ids (acked by the server). */
    protected abstract suspend fun clearPending(ids: List<String>)

    /** The local row for [id], or null if absent. */
    protected abstract suspend fun localRow(id: String): R?

    /** Upsert [rows] to the server; returns the ids the server acked. */
    protected abstract suspend fun remotePush(rows: List<R>): List<String>

    /** Fetch up to [pageSize] rows with `server_rev > cursor`, ordered by `server_rev`. */
    protected abstract suspend fun remotePull(cursor: Long, limit: Int): List<R>

    /** Upsert [rows] into Room in a single transaction. */
    protected abstract suspend fun applyPullBatch(rows: List<R>)

    /** True only when [row] is a currently-shared note (gates the conflict-copy safeguard). */
    protected open fun isSharedNote(row: R): Boolean = false

    /** Fork a dirty local shared note into a conflict copy before remote overwrites it. */
    protected open suspend fun conflictCopy(local: R) = Unit

    // ---- algorithm ----

    final override suspend fun push() {
        val dirty = dirtyRows()
        if (dirty.isEmpty()) return
        val acked = remotePush(dirty)
        if (acked.isNotEmpty()) clearPending(acked)
    }

    final override suspend fun pull() {
        var cursor = cursors.cursor(table)
        while (true) {
            val batch = remotePull(cursor, pageSize)
            if (batch.isEmpty()) break

            val toApply = ArrayList<R>(batch.size)
            for (remote in batch) {
                val local = localRow(remote.id)
                when (resolver.resolve(local, remote, isSharedNote = isSharedNote(remote))) {
                    SyncResolution.TakeRemote -> toApply += remote
                    SyncResolution.ConflictCopy -> {
                        local?.let { conflictCopy(it) }
                        toApply += remote
                    }
                    SyncResolution.KeepLocal -> Unit
                }
            }

            // Commit the batch, THEN advance the cursor — order matters for resumability.
            if (toApply.isNotEmpty()) applyPullBatch(toApply)
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
