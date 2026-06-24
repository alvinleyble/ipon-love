package com.iponlove.app.core.sync

import javax.inject.Inject
import javax.inject.Singleton

/**
 * What to do with a single pulled remote row, given the local row beside it.
 */
sealed interface SyncResolution {
    /** Upsert the remote row into Room; it is canonical. Clears any local dirty edits. */
    data object TakeRemote : SyncResolution

    /** Keep the local row untouched; the remote row is stale or already-known. No write. */
    data object KeepLocal : SyncResolution

    /**
     * Shared-note-only safeguard (ADR-0003): local has unpushed edits but remote is
     * newer, so remote wins the row — but instead of discarding the local edits, fork
     * them into a conflict-copy note first, then take remote as canonical.
     */
    data object ConflictCopy : SyncResolution
}

/**
 * Row-level last-write-wins, with the one localized safeguard for shared notes (ADR-0003).
 *
 * `server_rev` decides what is *new to fetch* (ADR-0002); this resolver decides who
 * *wins* a row, and it does so purely on [SyncMeta.updatedAt] (the client-set LWW key,
 * ADR-0001). The only lossy case — a locally dirty row that a newer remote overwrites —
 * discards local edits everywhere except a shared note, which conflict-copies instead.
 *
 * Tie-break: a strict `>` means equal timestamps favour the local row (skip the remote),
 * which keeps an unpushed local edit and makes re-pull idempotent.
 */
@Singleton
class ConflictResolver @Inject constructor() {

    /**
     * @param local        the current local row, or null if we have never seen this id.
     * @param remote       the freshly pulled row.
     * @param isSharedNote  true only for a row that is a currently-shared note, gating
     *                      the [SyncResolution.ConflictCopy] safeguard.
     */
    fun resolve(local: SyncMeta?, remote: SyncMeta, isSharedNote: Boolean): SyncResolution {
        // Never seen it: the server feed introduces it.
        if (local == null) return SyncResolution.TakeRemote

        val remoteIsNewer = remote.updatedAt.isAfter(local.updatedAt)

        // Clean local: the server feed is canonical. Apply only if strictly newer,
        // otherwise we already hold this version (or a just-pushed equal one) — skip.
        if (!local.pendingSync) {
            return if (remoteIsNewer) SyncResolution.TakeRemote else SyncResolution.KeepLocal
        }

        // Dirty local (unpushed edits). LWW: remote wins only if strictly newer.
        if (!remoteIsNewer) return SyncResolution.KeepLocal

        // Remote wins over a dirty local — the lossy case. Shared notes fork instead of lose.
        return if (isSharedNote) SyncResolution.ConflictCopy else SyncResolution.TakeRemote
    }
}
