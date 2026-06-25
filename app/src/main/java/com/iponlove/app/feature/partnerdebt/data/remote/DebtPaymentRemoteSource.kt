package com.iponlove.app.feature.partnerdebt.data.remote

import javax.inject.Inject

/**
 * The Supabase side of debt-payment sync. A port so the engine never depends on the
 * Supabase SDK directly.
 */
interface DebtPaymentRemoteSource {
    suspend fun push(rows: List<DebtPaymentDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<DebtPaymentDto>
}

/** No-op remote for offline development — rows stay `pending_sync` until the real backend. */
class StubDebtPaymentRemoteSource @Inject constructor() : DebtPaymentRemoteSource {
    override suspend fun push(rows: List<DebtPaymentDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<DebtPaymentDto> = emptyList()
}
