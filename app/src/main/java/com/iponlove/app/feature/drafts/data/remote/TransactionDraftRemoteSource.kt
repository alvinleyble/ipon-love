package com.iponlove.app.feature.drafts.data.remote

/** Remote port for the `transaction_drafts` table. Own-user-only; there is no partner variant. */
interface TransactionDraftRemoteSource {
    suspend fun push(rows: List<TransactionDraftDto>): List<String>

    suspend fun pull(cursor: Long, limit: Int): List<TransactionDraftDto>
}
