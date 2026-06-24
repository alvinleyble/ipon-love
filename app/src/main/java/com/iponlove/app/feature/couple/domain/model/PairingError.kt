package com.iponlove.app.feature.couple.domain.model

/** A user-facing reason a pairing action failed, mapped from the RPC's raised exception. */
enum class PairingError {
    ALREADY_IN_COUPLE,
    INVALID_INVITE_CODE,
    COUPLE_FULL,
    OWN_COUPLE,
    NOT_IN_COUPLE,
    NETWORK,
    UNKNOWN,
}

/** Thrown by the couple repository so the ViewModel can surface a typed [error]. */
class PairingException(val error: PairingError) : Exception()
