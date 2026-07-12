package com.iponlove.app.feature.settings.domain.repository

/**
 * Delete account (ADR-0045, v1.6.5 Item 6) — the server-side hard delete + local teardown.
 * Distinct from [ResetFinancesRepository]: reset keeps the account and only zeroes the numbers,
 * this destroys the account entirely (the one sanctioned exception to ADR-0010).
 */
interface AccountDeletionRepository {

    /**
     * Calls the `delete_account()` RPC (server cascade — the point of no return), then tears
     * down the local session and Room data so the app returns to the auth graph. If the RPC
     * throws, this aborts with nothing wiped (the account is intact); everything after it is
     * best-effort and always completes.
     */
    suspend fun deleteAccount()
}
