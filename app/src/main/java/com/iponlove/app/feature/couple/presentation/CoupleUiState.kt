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
) {
    val canCreate: Boolean get() = nameInput.isNotBlank() && selectedColor != null && !isWorking
    val canRedeem: Boolean get() = codeInput.isNotBlank() && selectedColor != null && !isWorking
}
