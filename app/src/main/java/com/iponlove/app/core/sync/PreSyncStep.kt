package com.iponlove.app.core.sync

/**
 * A hook that runs inside [SyncEngine.sync] before the standard table push/pull loop.
 *
 * Use for work that must complete before table rows are pushed — the primary case is
 * uploading local files to Supabase Storage and stamping the resulting URL onto the
 * row, so the table syncer sees a complete row ready to push.
 */
interface PreSyncStep {
    suspend fun run()
}
