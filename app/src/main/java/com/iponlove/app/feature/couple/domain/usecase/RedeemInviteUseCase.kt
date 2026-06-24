package com.iponlove.app.feature.couple.domain.usecase

import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import javax.inject.Inject

/** Join a couple by invite code. Codes are case-insensitive and trimmed. */
class RedeemInviteUseCase @Inject constructor(
    private val repository: CoupleRepository,
) {
    suspend operator fun invoke(code: String) =
        repository.redeemInvite(code.trim().uppercase())
}
