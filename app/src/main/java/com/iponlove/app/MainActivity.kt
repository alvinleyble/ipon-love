package com.iponlove.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.iponlove.app.core.sync.CoupleChannelManager
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.core.sync.SyncWorker
import com.iponlove.app.feature.budgets.worker.BudgetAlertWorker
import com.iponlove.app.core.ui.theme.IponTheme
import com.iponlove.app.feature.applock.domain.model.AppLockPreferences
import com.iponlove.app.feature.applock.domain.usecase.ObserveAppLockUseCase
import com.iponlove.app.feature.applock.presentation.AppLockManager
import com.iponlove.app.feature.applock.presentation.LockScreen
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.presentation.AuthScreen
import com.iponlove.app.feature.auth.presentation.AuthViewModel
import com.iponlove.app.feature.couple.domain.usecase.WatchUnpairUseCase
import com.iponlove.app.feature.recurring.domain.usecase.MaterializeRecurringRulesUseCase
import com.iponlove.app.feature.settings.data.ThemeDraftRepository
import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.usecase.ObserveThemePreferencesUseCase
import com.iponlove.app.feature.user.domain.usecase.EnsureCurrentUserRowUseCase
import androidx.glance.appwidget.updateAll
import com.iponlove.app.feature.widget.presentation.BalanceWidget
import com.iponlove.app.navigation.IponApp
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    @Inject lateinit var supabaseClient: SupabaseClient
    @Inject lateinit var materializeRecurringRules: MaterializeRecurringRulesUseCase
    @Inject lateinit var ensureCurrentUserRow: EnsureCurrentUserRowUseCase
    @Inject lateinit var watchUnpair: WatchUnpairUseCase
    @Inject lateinit var observeThemePreferences: ObserveThemePreferencesUseCase
    @Inject lateinit var observeAppLock: ObserveAppLockUseCase
    @Inject lateinit var appLockManager: AppLockManager
    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var coupleChannelManager: CoupleChannelManager
    @Inject lateinit var themeDraft: ThemeDraftRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleAuthDeepLink(intent)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appLockManager.cancelAutoLock()
                coupleChannelManager.setForeground(true)
                lifecycleScope.launch {
                    // Full refresh on every foreground resume — not only on login — so a
                    // partner's changes (and our own pending rows) converge immediately on
                    // return. Both calls are guarded by an active session so they're a no-op on
                    // the auth screen (materialize reads categories via CurrentUserProvider, which
                    // throws when logged out); sync shares the engine's single-flight lock, so it
                    // coalesces with the bell's catch-up pull rather than double-syncing.
                    if (supabaseClient.auth.currentUserOrNull() != null) {
                        materializeRecurringRules()
                        runCatching { syncEngine.sync() }
                    }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                appLockManager.scheduleAutoLock()
                coupleChannelManager.setForeground(false)
            }
        })
        enableEdgeToEdge()
        setContent {
            val savedTheme by observeThemePreferences()
                .collectAsState(initial = ThemePreferences())
            val draftTheme by themeDraft.draft.collectAsState()
            val themePreferences = draftTheme ?: savedTheme
            val appLockPrefs by observeAppLock()
                .collectAsState(initial = AppLockPreferences())
            val isLocked by appLockManager.isLocked.collectAsState()

            IponTheme(themePreferences = themePreferences) {
                val authViewModel: AuthViewModel = hiltViewModel()
                val status by authViewModel.status.collectAsState()
                // Drive the live-sync couple channel: connect when an authenticated, paired
                // user is foregrounded; tear down on sign-out (ADR-0015).
                LaunchedEffect(status) {
                    coupleChannelManager.setAuthenticatedUser(
                        (status as? AuthStatus.Authenticated)?.userId,
                    )
                }
                when (val current = status) {
                    is AuthStatus.Authenticated -> {
                        if (appLockPrefs.isPinSet && isLocked) {
                            LockScreen(isBiometricEnabled = appLockPrefs.isBiometricEnabled)
                        } else {
                            var initialSyncDone by remember(current.userId) { mutableStateOf(false) }
                            LaunchedEffect(current.userId) {
                                ensureCurrentUserRow()
                                runCatching { syncEngine.sync() }
                                initialSyncDone = true
                                materializeRecurringRules()
                                val wm = WorkManager.getInstance(applicationContext)
                                wm.enqueueUniqueWork(
                                    SyncWorker.WORK_NAME,
                                    ExistingWorkPolicy.KEEP,
                                    SyncWorker.buildRequest(),
                                )
                                wm.enqueueUniqueWork(
                                    BudgetAlertWorker.WORK_NAME,
                                    ExistingWorkPolicy.REPLACE,
                                    BudgetAlertWorker.buildRequest(),
                                )
                                BalanceWidget().updateAll(applicationContext)
                            }
                            LaunchedEffect(current.userId) { watchUnpair() }
                            if (initialSyncDone) {
                                IponApp(onSignOut = authViewModel::signOut)
                            } else {
                                SplashScreen()
                            }
                        }
                    }

                    AuthStatus.Unauthenticated -> AuthScreen(viewModel = authViewModel)
                    AuthStatus.Loading -> SplashScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthDeepLink(intent)
    }

    private fun handleAuthDeepLink(intent: Intent) {
        supabaseClient.handleDeeplinks(intent)
    }
}

@Composable
private fun SplashScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
