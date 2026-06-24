package com.iponlove.app.feature.couple.domain.usecase

import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * The single source of truth for the pairing UI. Pairing is driven entirely off the
 * current user's own `couple_id` (always readable even after RLS cuts off partner reads,
 * ADR-0008): null ⇒ not paired; otherwise the couple row + partner users row are joined in.
 *
 * A soft-deleted couple (mid-unpair, before the user row's `couple_id` clears) is treated
 * as not paired so the UI never shows a dissolved couple.
 */
class ObservePairingStateUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val coupleRepository: CoupleRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<PairingState> =
        userRepository.observeCurrentUser().flatMapLatest { me ->
            val coupleId = me?.coupleId
            when {
                me == null -> flowOf(PairingState.Loading)
                coupleId == null -> flowOf(PairingState.NotPaired)
                else -> combine(
                    coupleRepository.observeCouple(coupleId),
                    userRepository.observePartner(coupleId),
                ) { couple, partner ->
                    if (couple == null || couple.isDeleted) PairingState.NotPaired
                    else PairingState.Paired(couple, partner)
                }
            }
        }
}
