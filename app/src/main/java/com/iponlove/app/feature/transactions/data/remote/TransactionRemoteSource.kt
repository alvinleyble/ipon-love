package com.iponlove.app.feature.transactions.data.remote

import javax.inject.Inject

/**
 * The Supabase side of transactions sync. A port so the engine never depends on the
 * Supabase SDK directly; the real implementation lands with the backend slice.
 */
interface TransactionRemoteSource {
    suspend fun push(rows: List<TransactionDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<TransactionDto>

    /** Pull the partner's transactions from the `partner_transactions` redacting view (ADR-0005). */
    suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerTransactionDto>
}

/** No-op remote for offline development — rows stay `pending_sync` until the real backend. */
class StubTransactionRemoteSource @Inject constructor() : TransactionRemoteSource {
    override suspend fun push(rows: List<TransactionDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<TransactionDto> = emptyList()
    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerTransactionDto> = emptyList()
}
