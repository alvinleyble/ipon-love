package com.iponlove.app.feature.settings.data

import com.iponlove.app.feature.applock.presentation.AppLockManager
import com.iponlove.app.feature.settings.domain.repository.PrivacyModeRepository
import com.iponlove.app.feature.widget.presentation.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy mode is **session-scoped in-memory state** (v1.6.6 Item 25), not a persisted preference:
 * amounts default to hidden on every cold open, the user reveals them for the session, and they
 * re-hide once the app has been backgrounded past the app-lock grace period. There is no DataStore
 * anymore — a fresh process starts hidden, and nothing carries a reveal across process death.
 *
 * The re-hide rides [AppLockManager.autoLockElapsed] rather than a second timer, so a quick
 * background bounce under grace keeps the reveal while a longer absence re-hides — the same
 * boundary that engages the PIN lock (Item 25 decision, option b).
 */
@Singleton
class PrivacyModeRepositoryImpl @Inject constructor(
    appLockManager: AppLockManager,
    widgetRefresher: WidgetRefresher,
) : PrivacyModeRepository {

    // true = amounts hidden. Seeds hidden so the very first frame after a cold open is masked.
    private val hidden = MutableStateFlow(true)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            appLockManager.autoLockElapsed.collect {
                hidden.value = true
                // Repaint the balance widget AFTER the flag flips, so its snapshot reads the
                // re-hidden state. With the app lock no longer gating the widget (Item 33,
                // Alvin's call) this is the only re-mask signal a no-PIN user gets —
                // AppLockManager's own setLocked repaint dedups away when isLocked never cycles.
                widgetRefresher.refresh()
            }
        }
    }

    override fun observe(): Flow<Boolean> = hidden.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        hidden.value = enabled
    }
}
