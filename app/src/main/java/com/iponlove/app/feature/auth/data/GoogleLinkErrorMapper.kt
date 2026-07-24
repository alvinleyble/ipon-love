package com.iponlove.app.feature.auth.data

import com.iponlove.app.feature.auth.domain.model.AuthError

/**
 * Maps a failed `linkIdentityWithIdToken` call's message to a user-facing [AuthError] (ADR-0051
 * decision 5). Because the native link is synchronous, GoTrue's rejection surfaces at the call
 * site, so the two cases the redirect design had to collapse are distinguishable here: an
 * already-linked account gets its own message; everything else is a generic link failure. Pure —
 * matched on message substrings like [AuthErrorClassifier], so it stays JVM-unit-testable.
 */
internal object GoogleLinkErrorMapper {

    fun classify(message: String?): AuthError {
        val msg = message?.lowercase().orEmpty()
        return when {
            // GoTrue: "identity is already linked to another user" / "identity_already_exists" —
            // the chosen Google account belongs to a different Love, Ipon account.
            "already linked" in msg || "already exists" in msg ||
                "identity_already_exists" in msg -> AuthError.GOOGLE_ALREADY_LINKED
            "host" in msg || "connect" in msg || "timeout" in msg ||
                "network" in msg || "unreachable" in msg -> AuthError.NETWORK
            // Config-only "manual linking disabled" (a prerequisite we control) and anything else
            // fold into the generic link failure.
            else -> AuthError.GOOGLE_LINK_FAILED
        }
    }
}
