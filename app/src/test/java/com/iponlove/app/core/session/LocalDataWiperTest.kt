package com.iponlove.app.core.session

import com.iponlove.app.core.database.IponDatabase
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.data.SyncStatusStore
import com.iponlove.app.feature.applock.domain.repository.AppLockRepository
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
import com.iponlove.app.feature.widget.presentation.WidgetRefresher
import com.iponlove.app.navigation.NavConfigRepository
import com.iponlove.app.navigation.NavStateStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalDataWiperTest {

    private val database = mockk<IponDatabase>(relaxed = true)
    private val cursors = mockk<SyncCursorStore>(relaxed = true)
    private val navConfig = mockk<NavConfigRepository>(relaxed = true)
    private val navState = mockk<NavStateStore>(relaxed = true)
    private val onboarding = mockk<OnboardingRepository>(relaxed = true)
    private val appLock = mockk<AppLockRepository>(relaxed = true)
    private val syncStatus = mockk<SyncStatusStore>(relaxed = true)
    private val widgets = mockk<WidgetRefresher>(relaxed = true)
    private val wiper =
        LocalDataWiper(database, cursors, navConfig, navState, onboarding, appLock, syncStatus, widgets)

    @Test
    fun wipe_clearsRoom_resetsCursors_navConfig_onboardingFlags_andAppLockPin() = runTest {
        wiper.wipe()

        coVerify(exactly = 1) { database.clearAll() }
        coVerify(exactly = 1) { cursors.reset() }
        coVerify(exactly = 1) { navConfig.reset() }
        // The saved nav-restore location is per-device UI state pointing at the previous account's
        // last tab (Item 39) — clear it so the next account doesn't restore into it.
        coVerify(exactly = 1) { navState.clear() }
        coVerify(exactly = 1) { onboarding.reset() }
        // The app-lock PIN must be cleared so a switched-in account isn't locked behind the
        // previous user's code (cross-account isolation).
        coVerify(exactly = 1) { appLock.clearPin() }
        // The last-synced timestamp is the previous account's sync history (Item 9).
        coVerify(exactly = 1) { syncStatus.clear() }
    }

    @Test
    fun wipe_resetsCursors_beforeClearingRoom() = runTest {
        wiper.wipe()

        // Ordering invariant: an interrupted wipe with cursor=0 + stale Room self-heals on the
        // next pull, but empty Room + stale cursors wedges the couples/partner pulls forever
        // (`server_rev > cursor` never matches) — the app then shows "not paired" while the
        // server still has the couple.
        coVerifyOrder {
            cursors.reset()
            database.clearAll()
        }
    }

    @Test
    fun wipe_repaintsWidgets_afterRoomClears() = runTest {
        wiper.wipe()

        // The widget must repaint off the emptied state so the previous account's balances never
        // linger on the home screen across a sign-out/switch/delete (Alvin's on-device find,
        // 2026-07-14) — and only after the data it reads is gone.
        coVerifyOrder {
            database.clearAll()
            widgets.refresh()
        }
    }

    @Test
    fun wipe_survivesAWidgetRepaintFailure() = runTest {
        coEvery { widgets.refresh() } throws IllegalStateException("glance unavailable")

        wiper.wipe() // must not throw — a cosmetic repaint can't fail the wipe

        coVerify(exactly = 1) { database.clearAll() }
        coVerify(exactly = 1) { syncStatus.clear() }
    }
}
