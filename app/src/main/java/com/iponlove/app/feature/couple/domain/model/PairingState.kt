package com.iponlove.app.feature.couple.domain.model

import com.iponlove.app.feature.user.domain.model.User

/** The signed-in user's current pairing situation, derived from their own users row. */
sealed interface PairingState {

    /** Resolving the initial state (current user row not loaded yet). */
    data object Loading : PairingState

    /** Not in a couple — the UI offers "create" or "join by code". */
    data object NotPaired : PairingState

    /**
     * In a couple. [partner] is null while awaiting a join, or briefly before the partner's
     * users row has replicated in (it arrives via the normal users pull once paired).
     */
    data class Paired(val couple: Couple, val partner: User?) : PairingState
}
