package com.iponlove.app.core.sync

/**
 * One table's slice of a sync run. [SyncEngine] holds a list of these and drives them
 * in FK order (ADR-0009), push-all then pull-all. Feature data layers contribute one
 * per synced table, almost always by subclassing [BaseTableSyncer].
 */
interface TableSyncer {
    val table: SyncTable

    /**
     * Outbox push: send local dirty rows, clear `pending_sync` per acked row.
     *
     * @return true if rows were actually sent (acked). [SyncEngine.pushOnly] ORs these so a
     *   debounced write-push can ring the couple "bell" only when something really changed
     *   (ADR-0015); an empty/no-op push returns false.
     */
    suspend fun push(): Boolean

    /** Cursor pull: fetch `server_rev > cursor`, resolve, apply, advance cursor. */
    suspend fun pull()
}
