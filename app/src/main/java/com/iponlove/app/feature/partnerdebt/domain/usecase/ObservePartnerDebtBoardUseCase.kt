package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebtBoard
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * The partner-debt board, driven off the current user's own `couple_id` (always readable,
 * ADR-0008): emits null when not paired, otherwise the netted summary + debt list derived
 * from the live debt, payment, and partner streams. Mirrors [ObserveCoupleMembersUseCase]
 * so the screen re-derives everything from the single pairing signal.
 */
class ObservePartnerDebtBoardUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val repository: PartnerDebtRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<PartnerDebtBoard?> =
        userRepository.observeCurrentUser().flatMapLatest { me ->
            val coupleId = me?.coupleId
            if (me == null || coupleId == null) {
                flowOf(null)
            } else {
                combine(
                    userRepository.observePartner(coupleId),
                    repository.observeDebts(coupleId),
                    repository.observePayments(),
                ) { partner, debts, payments ->
                    PartnerDebtCalculator.summarize(me, partner, debts, payments)
                }
            }
        }
}
