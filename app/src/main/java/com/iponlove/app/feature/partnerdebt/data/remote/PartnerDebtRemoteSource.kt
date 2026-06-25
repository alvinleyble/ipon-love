package com.iponlove.app.feature.partnerdebt.data.remote

import javax.inject.Inject

/**
 * The Supabase side of partner-debt sync. A port so the engine never depends on the
 * Supabase SDK directly.
 */
interface PartnerDebtRemoteSource {
    suspend fun push(rows: List<PartnerDebtDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<PartnerDebtDto>
}

/** No-op remote for offline development — rows stay `pending_sync` until the real backend. */
class StubPartnerDebtRemoteSource @Inject constructor() : PartnerDebtRemoteSource {
    override suspend fun push(rows: List<PartnerDebtDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<PartnerDebtDto> = emptyList()
}
