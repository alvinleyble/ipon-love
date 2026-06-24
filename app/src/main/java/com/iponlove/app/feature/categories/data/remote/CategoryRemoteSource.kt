package com.iponlove.app.feature.categories.data.remote

import javax.inject.Inject

/**
 * The Supabase side of categories sync. A port so the engine never depends on the
 * Supabase SDK directly; the real implementation lands with the backend slice.
 */
interface CategoryRemoteSource {
    suspend fun push(rows: List<CategoryDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<CategoryDto>

    /** Pull the partner's categories from the `partner_categories` redacting view (ADR-0005). */
    suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerCategoryDto>
}

/** No-op remote for offline development — rows stay `pending_sync` until the real backend. */
class StubCategoryRemoteSource @Inject constructor() : CategoryRemoteSource {
    override suspend fun push(rows: List<CategoryDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<CategoryDto> = emptyList()
    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerCategoryDto> = emptyList()
}
