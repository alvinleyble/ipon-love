package com.iponlove.app.feature.settings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.LocalDataWiper
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import com.iponlove.app.feature.settings.data.AccountDeletionRepositoryImpl
import com.iponlove.app.feature.settings.data.remote.AccountDeletionRemoteSource
import io.mockk.Runs
import io.mockk.andThenJust
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Delete-account teardown ordering (ADR-0045). The RPC is the point of no return: it runs first,
 * and everything after it (session clear → wipe) is best-effort and must always complete.
 */
class AccountDeletionRepositoryImplTest {

    private val remote = mockk<AccountDeletionRemoteSource>()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val wiper = mockk<LocalDataWiper>(relaxed = true)
    private val repo = AccountDeletionRepositoryImpl(remote, authRepository, wiper)

    @Test
    fun success_rpcThenClearSessionThenWipe() = runTest {
        coEvery { remote.deleteAccount() } just Runs

        repo.deleteAccount()

        coVerifyOrder {
            remote.deleteAccount()
            authRepository.clearLocalSession()
            wiper.wipe()
        }
    }

    @Test
    fun rpcThrows_abortsWithNothingTornDown() = runTest {
        coEvery { remote.deleteAccount() } throws RuntimeException("rpc failed")

        var thrown = false
        try {
            repo.deleteAccount()
        } catch (_: RuntimeException) {
            thrown = true
        }

        assertThat(thrown).isTrue()
        coVerify(exactly = 0) { authRepository.clearLocalSession() }
        coVerify(exactly = 0) { wiper.wipe() }
    }

    @Test
    fun clearSessionThrows_wipeStillRuns() = runTest {
        coEvery { remote.deleteAccount() } just Runs
        coEvery { authRepository.clearLocalSession() } throws RuntimeException("dead session")

        repo.deleteAccount() // must not rethrow — teardown is best-effort past the RPC

        coVerify(exactly = 1) { wiper.wipe() }
    }

    @Test
    fun wipeThrowsOnce_retriedOnce() = runTest {
        coEvery { remote.deleteAccount() } just Runs
        coEvery { wiper.wipe() } throws RuntimeException("wipe 1") andThenJust Runs

        repo.deleteAccount() // must not rethrow

        coVerify(exactly = 2) { wiper.wipe() }
    }
}
