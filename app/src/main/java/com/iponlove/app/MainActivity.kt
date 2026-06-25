package com.iponlove.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import androidx.activity.compose.setContent
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.iponlove.app.core.sync.SyncWorker
import com.iponlove.app.core.ui.theme.IponTheme
import com.iponlove.app.feature.auth.domain.model.AuthStatus
import com.iponlove.app.feature.auth.presentation.AuthScreen
import com.iponlove.app.feature.auth.presentation.AuthViewModel
import com.iponlove.app.feature.couple.domain.usecase.WatchUnpairUseCase
import com.iponlove.app.feature.recurring.domain.usecase.MaterializeRecurringRulesUseCase
import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.usecase.ObserveThemePreferencesUseCase
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.feature.user.domain.usecase.EnsureCurrentUserRowUseCase
import androidx.glance.appwidget.updateAll
import com.iponlove.app.feature.widget.presentation.BalanceWidget
import com.iponlove.app.navigation.IponApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var supabaseClient: SupabaseClient
    @Inject lateinit var materializeRecurringRules: MaterializeRecurringRulesUseCase
    @Inject lateinit var ensureCurrentUserRow: EnsureCurrentUserRowUseCase
    @Inject lateinit var watchUnpair: WatchUnpairUseCase
    @Inject lateinit var observeThemePreferences: ObserveThemePreferencesUseCase
    @Inject lateinit var syncEngine: SyncEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthDeepLink(intent)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                lifecycleScope.launch { materializeRecurringRules() }
            }
        })
        enableEdgeToEdge()
        setContent {
            val themePreferences by observeThemePreferences()
                .collectAsState(initial = ThemePreferences())

            IponTheme(themePreferences = themePreferences) {
                val authViewModel: AuthViewModel = hiltViewModel()
                val status by authViewModel.status.collectAsState()
                when (val current = status) {
                    is AuthStatus.Authenticated -> {
                        var initialSyncDone by remember(current.userId) { mutableStateOf(false) }
                        LaunchedEffect(current.userId) {
                            ensureCurrentUserRow()
                            // In-process foreground sync on login — ensures Room is
                            // populated immediately on fresh install without waiting
                            // for WorkManager to schedule (ADR-0012).
                            runCatching { syncEngine.sync() }
                            initialSyncDone = true
                            materializeRecurringRules()
                            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                                SyncWorker.WORK_NAME,
                                ExistingWorkPolicy.KEEP,
                                SyncWorker.buildRequest(),
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
