package com.iponlove.app.feature.settings.data

import android.util.Log
import com.iponlove.app.core.session.LocalDataWiper
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.settings.data.remote.AccountDeletionRemoteSource
import com.iponlove.app.feature.settings.domain.repository.AccountDeletionRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Delete account (ADR-0045). Two phases with a hard line between them:
 *
 *  1. **The RPC** — `delete_account()` hard-deletes the caller's `auth.users` row server-side,
 *     cascading their entire account away. This is the **point of no return**: a throw here
 *     aborts the whole flow and nothing local is touched (the account is intact; the caller
 *     surfaces the error).
 *  2. **Local teardown** — everything past the RPC MUST complete, so it runs best-effort and is
 *     never allowed to rethrow: clear the local session ([AuthRepository.clearLocalSession], not
 *     [AuthRepository.signOut] — the server user is already gone, so a server revoke would be a
 *     doomed round-trip; this just drops the token and flips the app to the auth graph), then
 *     wipe Room et al. The wipe runs [NonCancellable] and is retried once (every step is
 *     idempotent) so a scope death mid-teardown can't leave a partial wipe — the
 *     [LocalDataWiper] ordering invariant — exactly like sign-out.
 */
class AccountDeletionRepositoryImpl @Inject constructor(
    private val remote: AccountDeletionRemoteSource,
    private val authRepository: AuthRepository,
    private val localDataWiper: LocalDataWiper,
) : AccountDeletionRepository {

    override suspend fun deleteAccount() {
        remote.deleteAccount() // point of no return — a throw here aborts with nothing wiped
        runCatching { authRepository.clearLocalSession() }
            .onFailure { Log.e(TAG, "clearLocalSession after delete failed", it) }
        wipeWithRetry()
    }

    private suspend fun wipeWithRetry() = withContext(NonCancellable) {
        try {
            localDataWiper.wipe()
        } catch (first: Exception) {
            Log.e(TAG, "post-delete wipe failed, retrying once", first)
            try {
                localDataWiper.wipe()
            } catch (second: Exception) {
                Log.e(TAG, "post-delete wipe failed twice; local state may be partial", second)
            }
        }
    }

    private companion object {
        const val TAG = "AccountDeletion"
    }
}
