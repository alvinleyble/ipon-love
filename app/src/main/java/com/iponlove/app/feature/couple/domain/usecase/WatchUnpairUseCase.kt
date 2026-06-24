package com.iponlove.app.feature.couple.domain.usecase

import com.iponlove.app.feature.user.domain.repository.UserRepository
import javax.inject.Inject

/**
 * Watches the current user's own `couple_id` and triggers the partner-replica purge the
 * moment it transitions from set → null — the device-local signal that the couple was
 * dissolved (ADR-0008), whether this user initiated the unpair or the partner did.
 *
 * Collects for the whole authenticated session; call once per login. The initial value is
 * only recorded (never treated as a transition) so an already-paired launch doesn't purge.
 */
class WatchUnpairUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val purgePartnerReplica: PurgePartnerReplicaUseCase,
) {
    suspend operator fun invoke() {
        var hadUser = false
        var previousCoupleId: String? = null
        userRepository.observeCurrentUser().collect { user ->
            if (user == null) return@collect
            val current = user.coupleId
            if (hadUser && previousCoupleId != null && current == null) {
                purgePartnerReplica()
            }
            hadUser = true
            previousCoupleId = current
        }
    }
}
