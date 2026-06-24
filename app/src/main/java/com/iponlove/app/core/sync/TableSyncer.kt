package com.iponlove.app.core.sync

/**
 * One table's slice of a sync run. [SyncEngine] holds a list of these and drives them
 * in FK order (ADR-0009), push-all then pull-all. Feature data layers contribute one
 * per synced table, almost always by subclassing [BaseTableSyncer].
 */
interface TableSyncer {
    val table: SyncTable

    /** Outbox push: send local dirty rows, clear `pending_sync` per acked row. */
    suspend fun push()

    /** Cursor pull: fetch `server_rev > cursor`, resolve, apply, advance cursor. */
    suspend fun pull()
}
