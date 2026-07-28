package com.iponlove.app.feature.onboarding.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingRepositoryImplTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun repo(name: String = "onboarding.preferences_pb"): OnboardingRepositoryImpl {
        val ds = PreferenceDataStoreFactory.create { tmpFolder.newFile(name) }
        return OnboardingRepositoryImpl(ds)
    }

    @Test
    fun isOnboardingDone_defaultsFalse() = runTest {
        assertThat(repo().isOnboardingDone()).isFalse()
    }

    @Test
    fun setOnboardingDone_reflectedByIsOnboardingDone() = runTest {
        val repo = repo()
        repo.setOnboardingDone()
        assertThat(repo.isOnboardingDone()).isTrue()
    }

    @Test
    fun dismissPairingCard_reflectedByObserve() = runTest {
        val repo = repo()
        repo.dismissPairingCard()
        repo.observePairingCardDismissed().test {
            assertThat(awaitItem()).isTrue()
            cancel()
        }
    }

    @Test
    fun observeWidgetNudgeLastShownAt_defaultsNull() = runTest {
        repo().observeWidgetNudgeLastShownAt().test {
            assertThat(awaitItem()).isNull()
            cancel()
        }
    }

    @Test
    fun recordWidgetNudgeShown_reflectedByObserve() = runTest {
        val repo = repo()
        repo.recordWidgetNudgeShown()
        repo.observeWidgetNudgeLastShownAt().test {
            assertThat(awaitItem()).isNotNull()
            cancel()
        }
    }

    @Test
    fun observeComingUpCollapsed_defaultsFalse() = runTest {
        repo().observeComingUpCollapsed().test {
            assertThat(awaitItem()).isFalse()
            cancel()
        }
    }

    @Test
    fun setComingUpCollapsed_reflectedByObserve() = runTest {
        val repo = repo()
        repo.setComingUpCollapsed(true)
        repo.observeComingUpCollapsed().test {
            assertThat(awaitItem()).isTrue()
            cancel()
        }
    }

    @Test
    fun setComingUpCollapsed_canBeUnset() = runTest {
        val repo = repo()
        repo.setComingUpCollapsed(true)
        repo.setComingUpCollapsed(false)
        repo.observeComingUpCollapsed().test {
            assertThat(awaitItem()).isFalse()
            cancel()
        }
    }

    @Test
    fun reset_clearsOnboardingDone_pairingCardDismissed_widgetNudgeLastShownAt_andComingUpCollapsed() = runTest {
        // LocalDataWiper calls this on sign-out/account-switch (ADR-0024 addendum) so a
        // different account signing in on the same device still gets the guided onboarding
        // graph, including starter-template seeding — not a permanently-tripped device flag.
        val repo = repo()
        repo.setOnboardingDone()
        repo.dismissPairingCard()
        repo.recordWidgetNudgeShown()
        repo.setComingUpCollapsed(true)

        repo.reset()

        assertThat(repo.isOnboardingDone()).isFalse()
        repo.observePairingCardDismissed().test {
            assertThat(awaitItem()).isFalse()
            cancel()
        }
        repo.observeWidgetNudgeLastShownAt().test {
            assertThat(awaitItem()).isNull()
            cancel()
        }
        repo.observeComingUpCollapsed().test {
            assertThat(awaitItem()).isFalse()
            cancel()
        }
    }
}
