package com.iponlove.app.core.session

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AccountSwitchGuardTest {

    private val wiper = mockk<LocalDataWiper>(relaxed = true)

    private fun guardWithLastUser(last: String?): Pair<AccountSwitchGuard, LastActiveUserStore> {
        val store = mockk<LastActiveUserStore>(relaxed = true)
        coEvery { store.lastUserId() } returns last
        return AccountSwitchGuard(store, wiper) to store
    }

    @Test
    fun firstEverLogin_doesNotWipe_butRecordsUser() = runTest {
        val (guard, store) = guardWithLastUser(last = null)

        val wiped = guard.onAuthenticated("user-A")

        assertThat(wiped).isFalse()
        coVerify(exactly = 0) { wiper.wipe() }
        coVerify(exactly = 1) { store.setLastUserId("user-A") }
    }

    @Test
    fun sameUserAgain_doesNotWipe() = runTest {
        val (guard, store) = guardWithLastUser(last = "user-A")

        val wiped = guard.onAuthenticated("user-A")

        assertThat(wiped).isFalse()
        coVerify(exactly = 0) { wiper.wipe() }
        coVerify(exactly = 1) { store.setLastUserId("user-A") }
    }

    @Test
    fun differentUser_wipesThenRecordsNewUser() = runTest {
        val (guard, store) = guardWithLastUser(last = "user-A")

        val wiped = guard.onAuthenticated("user-B")

        assertThat(wiped).isTrue()
        coVerify(exactly = 1) { wiper.wipe() }
        coVerify(exactly = 1) { store.setLastUserId("user-B") }
    }
}
