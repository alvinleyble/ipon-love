package com.iponlove.app.core.sync

import java.time.Instant

/** Minimal [SyncMeta] row for exercising the engine without any real entity. */
data class FakeRow(
    override val id: String,
    override val updatedAt: Instant,
    override val serverRev: Long? = null,
    override val isDeleted: Boolean = false,
    override val pendingSync: Boolean = false,
) : SyncMeta

/** In-memory [SyncCursorStore] for tests. */
class InMemoryCursorStore : SyncCursorStore {
    private val cursors = mutableMapOf<SyncTable, Long>()
    override suspend fun cursor(table: SyncTable): Long = cursors[table] ?: 0L
    override suspend fun setCursor(table: SyncTable, value: Long) {
        cursors[table] = value
    }
    override suspend fun reset() = cursors.clear()
}

/** Fixed instant helper. */
fun at(epochMillis: Long): Instant = Instant.ofEpochMilli(epochMillis)
