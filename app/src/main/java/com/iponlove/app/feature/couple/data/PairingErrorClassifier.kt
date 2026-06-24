package com.iponlove.app.feature.couple.data

import com.iponlove.app.feature.couple.domain.model.PairingError

/**
 * Maps a failed pairing RPC's message to a user-facing [PairingError]. The pairing RPCs
 * `raise exception '<text>'` (schema.sql, ADR-0006/0008); PostgREST surfaces that text in
 * the thrown exception's message. We match on those substrings rather than the SDK's
 * exception class hierarchy. Pure — unit-tested.
 */
internal object PairingErrorClassifier {

    fun classify(message: String?): PairingError {
        val msg = message?.lowercase().orEmpty()
        return when {
            "already in a couple" in msg -> PairingError.ALREADY_IN_COUPLE
            "invalid invite code" in msg -> PairingError.INVALID_INVITE_CODE
            "already full" in msg -> PairingError.COUPLE_FULL
            "your own couple" in msg -> PairingError.OWN_COUPLE
            "not in a couple" in msg || "no couple" in msg ||
                "not a member" in msg -> PairingError.NOT_IN_COUPLE
            "host" in msg || "connect" in msg || "timeout" in msg ||
                "network" in msg || "unreachable" in msg -> PairingError.NETWORK
            else -> PairingError.UNKNOWN
        }
    }
}
