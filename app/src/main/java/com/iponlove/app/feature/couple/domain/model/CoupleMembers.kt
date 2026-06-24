package com.iponlove.app.feature.couple.domain.model

import com.iponlove.app.feature.user.domain.model.User

/**
 * Both members of a couple: the signed-in user [me] and their [partner] (null until the
 * partner's row has synced in, or while the invite is still unredeemed). Emitted only
 * while paired — see ObserveCoupleMembersUseCase.
 */
data class CoupleMembers(
    val me: User,
    val partner: User?,
)
