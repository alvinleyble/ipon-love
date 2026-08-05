package com.iponlove.app.feature.settings.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Receipt-scan preferences (v1.7.3 Item 2). Local-only and device-global, mirroring
 * [NotificationPreferencesRepository]'s shape — nothing here syncs.
 */
interface ReceiptPreferencesRepository {
    /**
     * Whether a scanned receipt also lands in the `Pictures/Love, Ipon` album (ADR-0062 decision
     * 7) — **default ON**. The switch exists because gallery photos sync to Google Photos, so a
     * user's grocery and pharmacy receipts would otherwise appear in a backed-up, possibly shared
     * camera roll: that should be a choice, not a surprise. The copy itself is free and never
     * gated — it costs no Storage egress, no sync and no cap.
     */
    fun observeGalleryCopyEnabled(): Flow<Boolean>
    suspend fun setGalleryCopyEnabled(enabled: Boolean)
}
