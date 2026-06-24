package com.iponlove.app.feature.couple.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.couple.domain.model.PairingException
import com.iponlove.app.feature.couple.domain.usecase.CreateCoupleUseCase
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.couple.domain.usecase.RedeemInviteUseCase
import com.iponlove.app.feature.couple.domain.usecase.RotateInviteCodeUseCase
import com.iponlove.app.feature.couple.domain.usecase.UnpairUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CoupleViewModel @Inject constructor(
    observePairingState: ObservePairingStateUseCase,
    private val createCoupleUseCase: CreateCoupleUseCase,
    private val redeemInviteUseCase: RedeemInviteUseCase,
    private val rotateInviteCodeUseCase: RotateInviteCodeUseCase,
    private val unpairUseCase: UnpairUseCase,
) : ViewModel() {

    /** Transient form/operation state; the pairing situation streams in separately. */
    private val local = MutableStateFlow(CoupleUiState())

    val state: StateFlow<CoupleUiState> =
        combine(observePairingState(), local) { pairing, l ->
            l.copy(pairing = pairing)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CoupleUiState(),
        )

    fun onNameChange(value: String) = local.update { it.copy(nameInput = value, error = null) }

    fun onCodeChange(value: String) = local.update { it.copy(codeInput = value, error = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    fun createCouple() = mutate(clearInput = Input.NAME) { createCoupleUseCase(local.value.nameInput) }

    fun redeemInvite() = mutate(clearInput = Input.CODE) { redeemInviteUseCase(local.value.codeInput) }

    fun rotateInviteCode() = mutate(clearInput = null) { rotateInviteCodeUseCase() }

    fun unpair() = mutate(clearInput = null) { unpairUseCase() }

    private enum class Input { NAME, CODE }

    /**
     * Run a pairing mutation: flip [CoupleUiState.isWorking], surface a [PairingException]
     * as a typed error, and clear the named input on success. The observed pairing flow
     * refreshes the screen once the sync inside the mutation completes.
     */
    private fun mutate(clearInput: Input?, block: suspend () -> Unit) {
        if (local.value.isWorking) return
        local.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                local.update {
                    it.copy(
                        isWorking = false,
                        nameInput = if (clearInput == Input.NAME) "" else it.nameInput,
                        codeInput = if (clearInput == Input.CODE) "" else it.codeInput,
                    )
                }
            } catch (e: PairingException) {
                local.update { it.copy(isWorking = false, error = e.error) }
            }
        }
    }
}
