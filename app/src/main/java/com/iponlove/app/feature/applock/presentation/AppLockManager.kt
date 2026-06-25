package com.iponlove.app.feature.applock.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class AppLockManager @Inject constructor() {

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lockJob: Job? = null

    fun unlock() { _isLocked.value = false }
    fun lock() { _isLocked.value = true }

    fun scheduleAutoLock() {
        lockJob?.cancel()
        lockJob = scope.launch {
            delay(GRACE_PERIOD_MS)
            _isLocked.value = true
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
