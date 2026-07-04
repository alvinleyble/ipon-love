package com.iponlove.app.feature.applock

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.applock.data.AppLockRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class AppLockRepositoryImplTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun repo(name: String = "applock.preferences_pb"): AppLockRepositoryImpl {
        val ds = PreferenceDataStoreFactory.create { tmpFolder.newFile(name) }
        return AppLockRepositoryImpl(ds)
    }

    @Test
    fun setPin_thenVerify_succeeds() = runTest {
        val repo = repo()
        repo.setPin("1234")
        assertThat(repo.verifyPin("1234")).isTrue()
    }

    @Test
    fun verifyPin_wrongPin_returnsFalse() = runTest {
        val repo = repo()
        repo.setPin("1234")
        assertThat(repo.verifyPin("0000")).isFalse()
    }

    @Test
    fun verifyPin_noPinSet_returnsFalse() = runTest {
        assertThat(repo().verifyPin("1234")).isFalse()
    }

    @Test
    fun clearPin_removesHashAndSetsPinSetFalse() = runTest {
        val repo = repo()
        repo.setPin("1234")
        repo.clearPin()
        assertThat(repo.verifyPin("1234")).isFalse()
        repo.observe().test {
            assertThat(awaitItem().isPinSet).isFalse()
            cancel()
        }
    }

    @Test
    fun clearPin_disablesBiometric() = runTest {
        val repo = repo()
        repo.setPin("1234")
        repo.setBiometricEnabled(true)
        repo.clearPin()
        repo.observe().test {
            assertThat(awaitItem().isBiometricEnabled).isFalse()
            cancel()
        }
    }

    @Test
    fun setBiometricEnabled_true_reflectedInPrefs() = runTest {
        val repo = repo()
        repo.setBiometricEnabled(true)
        repo.observe().test {
            assertThat(awaitItem().isBiometricEnabled).isTrue()
            cancel()
        }
    }

    @Test
    fun setPin_isPinSetTrue_observedImmediately() = runTest {
        val repo = repo()
        repo.setPin("9876")
        repo.observe().test {
            assertThat(awaitItem().isPinSet).isTrue()
            cancel()
        }
    }

    @Test
    fun setPin_replacesPreviousPin() = runTest {
        val repo = repo()
        repo.setPin("1111")
        repo.setPin("2222")
        assertThat(repo.verifyPin("1111")).isFalse()
        assertThat(repo.verifyPin("2222")).isTrue()
    }

    @Test
    fun biometricNudgeShown_defaultsFalse() = runTest {
        repo().observe().test {
            assertThat(awaitItem().biometricNudgeShown).isFalse()
            cancel()
        }
    }

    @Test
    fun markBiometricNudgeShown_reflectedInPrefs() = runTest {
        val repo = repo()
        repo.markBiometricNudgeShown()
        repo.observe().test {
            assertThat(awaitItem().biometricNudgeShown).isTrue()
            cancel()
        }
    }

    @Test
    fun clearPin_resetsBiometricNudgeShown() = runTest {
        val repo = repo()
        repo.setPin("1234")
        repo.markBiometricNudgeShown()
        repo.clearPin()
        repo.observe().test {
            assertThat(awaitItem().biometricNudgeShown).isFalse()
            cancel()
        }
    }

    @Test
    fun setPinAttemptState_reflectedInPrefs() = runTest {
        val repo = repo()
        val lockoutUntil = Instant.parse("2026-07-04T00:00:30Z")
        repo.setPinAttemptState(5, lockoutUntil)
        repo.observe().test {
            val prefs = awaitItem()
            assertThat(prefs.failedPinAttempts).isEqualTo(5)
            assertThat(prefs.pinLockoutUntil).isEqualTo(lockoutUntil)
            cancel()
        }
    }

    @Test
    fun setPinAttemptState_zeroAttemptsAndNullLockout_clearsState() = runTest {
        val repo = repo()
        repo.setPinAttemptState(5, Instant.parse("2026-07-04T00:00:30Z"))
        repo.setPinAttemptState(0, null)
        repo.observe().test {
            val prefs = awaitItem()
            assertThat(prefs.failedPinAttempts).isEqualTo(0)
            assertThat(prefs.pinLockoutUntil).isNull()
            cancel()
        }
    }

    @Test
    fun setPin_resetsAnyExistingLockoutState() = runTest {
        val repo = repo()
        repo.setPin("1234")
        repo.setPinAttemptState(5, Instant.parse("2026-07-04T00:00:30Z"))
        repo.setPin("5678")
        repo.observe().test {
            val prefs = awaitItem()
            assertThat(prefs.failedPinAttempts).isEqualTo(0)
            assertThat(prefs.pinLockoutUntil).isNull()
            cancel()
        }
    }

    @Test
    fun clearPin_resetsLockoutState() = runTest {
        val repo = repo()
        repo.setPin("1234")
        repo.setPinAttemptState(5, Instant.parse("2026-07-04T00:00:30Z"))
        repo.clearPin()
        repo.observe().test {
            val prefs = awaitItem()
            assertThat(prefs.failedPinAttempts).isEqualTo(0)
            assertThat(prefs.pinLockoutUntil).isNull()
            cancel()
        }
    }
}
