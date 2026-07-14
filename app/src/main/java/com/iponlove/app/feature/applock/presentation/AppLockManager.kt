package com.iponlove.app.feature.applock.presentation

import com.iponlove.app.feature.widget.presentation.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the runtime lock state and the 30-second auto-lock timer (ADR design: Slice C).
 * Starts locked so a fresh process always prompts for PIN (if set) before showing app data.
 * MainActivity registers a lifecycle observer that calls [scheduleAutoLock]/[cancelAutoLock].
 */
@Singleton
class AppLockManager @Inject constructor(
    private val widgetRefresher: WidgetRefresher,
) {

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    /**
     * Fires each time the background grace period elapses (the auto-lock timer completing). Unlike
     * [isLocked] — which dedups and, for a no-PIN user, never leaves `true` to cycle — this emits on
     * every grace boundary regardless of PIN state, so features that re-hide on inactivity (privacy
     * mode, v1.6.6 Item 25) can ride this one timer instead of running their own.
     */
    private val _autoLockElapsed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val autoLockElapsed: SharedFlow<Unit> = _autoLockElapsed.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lockJob: Job? = null

    fun unlock() = setLocked(false)
    fun lock() = setLocked(true)

    /**
     * Single writer for the lock flag so every transition (manual lock/unlock and the auto-lock
     * timer) also repaints the balance widget — a widget that keeps showing the balance after the
     * app locks would defeat the lock (grill 2026-07-14). Independent of Compose lifecycle so the
     * background auto-lock still masks the home screen.
     */
    private fun setLocked(value: Boolean) {
        if (_isLocked.value == value) return
        _isLocked.value = value
        scope.launch { widgetRefresher.refresh() }
    }

    fun scheduleAutoLock() {
        lockJob?.cancel()
        lockJob = scope.launch {
            delay(GRACE_PERIOD_MS)
            setLocked(true)
            _autoLockElapsed.tryEmit(Unit)
        }
    }

    fun cancelAutoLock() {
        lockJob?.cancel()
        lockJob = null
    }

    companion object {
        private const val GRACE_PERIOD_MS = 30_000L
    }
}
