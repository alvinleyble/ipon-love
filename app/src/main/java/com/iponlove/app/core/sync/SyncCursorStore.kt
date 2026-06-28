package com.iponlove.app.core.sync

/**
 * Per-table pull cursor: the max `server_rev` committed locally for a table (ADR-0002).
 *
 * Persisted client-side (DataStore). A fresh device starts every table at 0 and pulls
 * the full history (tombstones excluded server-side, ADR-0010). The cursor is advanced
 * **only after** the table's pull batch commits to Room, so an interrupted sync re-pulls
 * rather than skips (ADR-0009).
 */
interface SyncCursorStore {
    suspend fun cursor(table: SyncTable): Long
    suspend fun setCursor(table: SyncTable, value: Long)

    /**
     * Reset every table's cursor to 0 so the next sync re-pulls the full history. Used by the
     * sign-out / account-switch wipe (ADR-0021): after Room is cleared the cursors must drop
     * too, otherwise a re-login would skip every row whose `server_rev` sits below the stale
     * cursor and the account would appear empty.
     */
    suspend fun reset()
}
