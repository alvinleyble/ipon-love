package com.iponlove.app.feature.auth.domain.model

/**
 * A third-party identity linked to the signed-in account (ADR-0051). Currently only Google is
 * surfaced (in-app "Connect Google account"). [email] is the address on that identity's OIDC
 * claims, which may differ from the account's sign-in email — or be null if the provider didn't
 * supply one.
 */
data class LinkedIdentity(val email: String?)
