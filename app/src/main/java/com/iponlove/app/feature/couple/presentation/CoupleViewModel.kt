package com.iponlove.app.feature.couple.presentation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.couple.domain.model.PairingException
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.CreateCoupleUseCase
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.couple.domain.usecase.RedeemInviteUseCase
import com.iponlove.app.feature.couple.domain.usecase.RemoveCoupleBannerUseCase
import com.iponlove.app.feature.couple.domain.usecase.RotateInviteCodeUseCase
import com.iponlove.app.feature.couple.domain.usecase.SetCoupleBannerUseCase
import com.iponlove.app.feature.couple.domain.usecase.UnpairUseCase
import com.iponlove.app.feature.user.domain.repository.UserRepository
import com.iponlove.app.feature.user.domain.usecase.UpdateAccentColorUseCase
import com.iponlove.app.navigation.PinCoupleShortcutUseCase
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
    private val updateAccentColorUseCase: UpdateAccentColorUseCase,
    private val pinCoupleShortcutUseCase: PinCoupleShortcutUseCase,
    private val userRepository: UserRepository,
    private val setCoupleBannerUseCase: SetCoupleBannerUseCase,
    private val removeCoupleBannerUseCase: RemoveCoupleBannerUseCase,
    premiumGate: PremiumGate,
) : ViewModel() {

    private val local = MutableStateFlow(CoupleUiState())

    val state: StateFlow<CoupleUiState> =
        combine(
            observePairingState(),
            local,
            userRepository.observeCurrentUser(),
            // Item 10: the couple-photo gate is SHARED (either partner's premium unlocks it); the
            // first shared soft gate. True while dormant, so the affordance works pre-flip.
            premiumGate.observeLocked(Scope.SHARED),
        ) { pairing, l, me, bannerLocked ->
            l.copy(
                pairing = pairing,
                currentDisplayName = me?.displayName,
                currentAvatarMotif = me?.avatarMotif,
                currentAccentColor = me?.accentColor,
                bannerUnlocked = !bannerLocked,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CoupleUiState(),
        )

    fun onNameChange(value: String) = local.update { it.copy(nameInput = value, error = null) }

    fun onCodeChange(value: String) = local.update { it.copy(codeInput = value, error = null) }

    fun onColorSelected(hex: String) = local.update { it.copy(selectedColor = hex) }

    fun dismissError() = local.update { it.copy(error = null) }

    fun createCouple() = mutate(clearInput = Input.NAME) {
        createCoupleUseCase(local.value.nameInput)
        local.value.selectedColor?.let { updateAccentColorUseCase(it) }
        // The user just expressed couple intent — surface Couple on the bar (B/C flows).
        pinCoupleShortcutUseCase()
    }

    fun redeemInvite() = mutate(clearInput = Input.CODE) {
        redeemInviteUseCase(local.value.codeInput)
        local.value.selectedColor?.let { updateAccentColorUseCase(it) }
        pinCoupleShortcutUseCase()
    }

    fun rotateInviteCode() = mutate(clearInput = null) { rotateInviteCodeUseCase() }

    fun unpair() = mutate(clearInput = null) { unpairUseCase() }

    /** Set the couple photo from an already-cropped [bitmap] (Item 10). No-op unless fully paired
     *  and unlocked — the tap-time gate lives in the UI, this is the last-line guard. */
    fun setCoupleBanner(bitmap: Bitmap) {
        val paired = state.value.pairing as? PairingState.Paired ?: return
        if (!state.value.bannerUnlocked) return
        bannerMutate { setCoupleBannerUseCase(paired.couple.id, paired.couple.bannerUrl, bitmap) }
    }

    /** Clear the couple photo, reverting both surfaces to the derived gradient (Item 10). */
    fun removeCoupleBanner() {
        val paired = state.value.pairing as? PairingState.Paired ?: return
        bannerMutate { removeCoupleBannerUseCase(paired.couple.bannerUrl) }
    }

    fun dismissBannerError() = local.update { it.copy(bannerError = null) }

    private fun bannerMutate(block: suspend () -> Unit) {
        if (local.value.isBannerWorking) return
        local.update { it.copy(isBannerWorking = true, bannerError = null) }
        viewModelScope.launch {
            try {
                block()
                local.update { it.copy(isBannerWorking = false) }
            } catch (_: Exception) {
                local.update {
                    it.copy(isBannerWorking = false, bannerError = "Couldn't update the photo. Try again.")
                }
            }
        }
    }

    private enum class Input { NAME, CODE }

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
