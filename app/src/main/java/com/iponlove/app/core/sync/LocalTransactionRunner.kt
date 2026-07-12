package com.iponlove.app.core.sync

import androidx.room.withTransaction
import com.iponlove.app.core.database.IponDatabase
import javax.inject.Inject

/**
 * Runs [block] as one atomic local write spanning however many DAOs it touches, so a
 * mid-batch failure can't leave a partial cross-table change (Reset finances, ADR-0037; any
 * future owned-row bulk wipe). A thin seam over [IponDatabase.withTransaction] — real Room
 * transactions need an opened database and can't run against a mock, so call sites depend on
 * this interface instead of [IponDatabase] directly to stay unit-testable.
 */
fun interface LocalTransactionRunner {
    suspend fun run(block: suspend () -> Unit)
}

class RoomTransactionRunner @Inject constructor(
    private val database: IponDatabase,
) : LocalTransactionRunner {
    override suspend fun run(block: suspend () -> Unit) = database.withTransaction { block() }
}
