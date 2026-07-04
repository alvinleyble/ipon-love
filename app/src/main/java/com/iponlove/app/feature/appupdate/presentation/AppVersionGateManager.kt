package com.iponlove.app.feature.appupdate.presentation

import com.iponlove.app.feature.appupdate.domain.usecase.CheckAppVersionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the runtime version-mismatch flag (ADR-0029). Starts not-blocked so a slow/offline
 * check never gates the app before its first result lands (fail-open). MainActivity calls
 * [check] on the same foreground/resume lifecycle observer AppLock already uses.
 */
@Singleton
class AppVersionGateManager @Inject constructor(
    private val checkAppVersion: CheckAppVersionUseCase,
) {
    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    suspend fun check(installedVersionCode: Int) {
        _isBlocked.value = checkAppVersion(installedVersionCode)
    }
}
