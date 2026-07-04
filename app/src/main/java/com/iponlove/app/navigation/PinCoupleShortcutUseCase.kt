package com.iponlove.app.navigation

import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Pin the Couple module to the bottom bar, swapping out Manage if pinned (else the last pin).
 * Called by the couple flow right after a successful create-couple or redeem-invite — the user
 * just expressed couple intent, so the bar should surface Couple without a manual editor trip
 * (2026-07-04 redesign). No-op if Couple is already pinned. This is the ONLY pairing-driven
 * config write: passive pairing-state changes (partner redeems your code, unpair) never touch
 * the layout.
 */
class PinCoupleShortcutUseCase @Inject constructor(
    private val navConfigRepository: NavConfigRepository,
) {
    suspend operator fun invoke() {
        val current = navConfigRepository.observe().first()
        val updated = current.ensurePinned(NavRegistry.COUPLE.id, NavRegistry.MANAGE.id)
        if (updated != current) navConfigRepository.save(updated)
    }
}
