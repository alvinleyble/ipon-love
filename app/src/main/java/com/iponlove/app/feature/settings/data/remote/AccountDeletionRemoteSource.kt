package com.iponlove.app.feature.settings.data.remote

/** Calls the `delete_account()` SECURITY DEFINER RPC (ADR-0045). A thin port so the repository's
 *  teardown orchestration stays JVM-testable. */
interface AccountDeletionRemoteSource {
    suspend fun deleteAccount()
}
