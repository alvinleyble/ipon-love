package com.iponlove.app.feature.couple.presentation

import com.iponlove.app.feature.couple.domain.model.PairingError
import com.iponlove.app.feature.couple.domain.model.PairingState

/**
 * Screen state for the Couple screen: the observed [pairing] situation plus the transient
 * form/operation state ([nameInput], [codeInput], [isWorking], [error]).
 */
data class CoupleUiState(
    val pairing: PairingState = PairingState.Loading,
    val nameInput: String = "",
    val codeInput: String = "",
    val isWorking: Boolean = false,
    val error: PairingError? = null,
    val selectedColor: String? = null,
    val currentDisplayName: String? = null,
    // Current user's synced cosmetics, for the paired identity card's avatars (Item 3 Leg 1).
    // The partner's motif/accent ride on PairingState.Paired.partner (a User).
    val currentAvatarMotif: String? = null,
    val currentAccentColor: String? = null,
) {
    val canCreate: Boolean get() = nameInput.isNotBlank() && selectedColor != null && !isWorking
    val canRedeem: Boolean get() = codeInput.isNotBlank() && selectedColor != null && !isWorking
}
