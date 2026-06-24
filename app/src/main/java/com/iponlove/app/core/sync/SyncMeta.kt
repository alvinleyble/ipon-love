package com.iponlove.app.core.sync

import java.time.Instant

/**
 * The sync bookkeeping every synced row carries, surfaced to the sync engine
 * uniformly so the engine never needs to know an entity's concrete shape.
 *
 * Implemented by each feature's Room entity. A row mapped *from* the server is
 * still presented as a [SyncMeta] (entity-shaped) with [pendingSync] = false —
 * the entity↔DTO mapping (which drops `pending_sync` on the wire, ADR-0002)
 * lives inside that feature's [TableSyncer], never here.
 *
 *  - [updatedAt]   client-set LWW key (ADR-0001) — the conflict comparison key.
 *  - [serverRev]   server-assigned pull cursor (ADR-0002); null until the row has
 *                  been seen by the server (i.e. local-only, never pushed).
 *  - [isDeleted]   soft-delete tombstone (ADR-0010) — synced like any other field.
 *  - [pendingSync] local-only outbox flag (ADR-0002) — true while a local write
 *                  awaits push; NEVER sent to Supabase.
 */
interface SyncMeta {
    val id: String
    val updatedAt: Instant
    val serverRev: Long?
    val isDeleted: Boolean
    val pendingSync: Boolean
}
