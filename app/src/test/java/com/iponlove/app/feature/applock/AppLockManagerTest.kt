package com.iponlove.app.feature.applock

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.applock.presentation.AppLockManager
import org.junit.Test

class AppLockManagerTest {

    @Test
    fun startsLocked() {
        assertThat(AppLockManager().isLocked.value).isTrue()
    }

    @Test
    fun unlock_setsLockedFalse() {
        val manager = AppLockManager()
        manager.unlock()
        assertThat(manager.isLocked.value).isFalse()
    }

    @Test
    fun lock_setsLockedTrue() {
        val manager = AppLockManager()
        manager.unlock()
        manager.lock()
        assertThat(manager.isLocked.value).isTrue()
    }

    @Test
    fun cancelAutoLock_beforeTimerFires_preservesUnlockedState() {
        val manager = AppLockManager()
        manager.unlock()
        manager.scheduleAutoLock()
        manager.cancelAutoLock()
        assertThat(manager.isLocked.value).isFalse()
    }
}
