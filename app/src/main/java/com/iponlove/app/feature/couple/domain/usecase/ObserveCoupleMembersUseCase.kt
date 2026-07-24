package com.iponlove.app.feature.couple.domain.usecase

import com.iponlove.app.feature.couple.domain.model.CoupleMembers
import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Both members of the couple, driven off the current user's own `couple_id` (always
 * readable, ADR-0008): emits null when not paired, otherwise the user joined with their
 * partner's replicated row and the couple's name. The combined view uses this for
 * color/name attribution and the identity banner (v1.7.0 Item 9).
 */
class ObserveCoupleMembersUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val coupleRepository: CoupleRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<CoupleMembers?> =
        userRepository.observeCurrentUser().flatMapLatest { me ->
            val coupleId = me?.coupleId
            if (me == null || coupleId == null) {
                flowOf(null)
            } else {
                combine(
                    userRepository.observePartner(coupleId),
                    coupleRepository.observeCouple(coupleId),
                ) { partner, couple ->
                    CoupleMembers(me, partner, couple?.name, couple?.bannerUrl)
                }
            }
        }
}
