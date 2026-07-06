package com.iponlove.app.feature.transactions.data.remote

/** Supabase side of `transaction_images` sync — Postgrest row metadata only (no Storage calls). */
interface TransactionImageRemoteSource {
    suspend fun push(rows: List<TransactionImageDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<TransactionImageDto>
    suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerTransactionImageDto>
}
