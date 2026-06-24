package com.iponlove.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.iponlove.app.feature.user.domain.usecase.EnsureCurrentUserRowUseCase
import com.iponlove.app.navigation.IponApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Generates any recurring transactions that came due while the app was closed (ADR-0012:
     *  interactive catch-up runs in-process; WorkManager owns background retry later). Only run
     *  once authenticated — it writes owned rows that need the signed-in user's id. */
    @Inject lateinit var materializeRecurringRules: MaterializeRecurringRulesUseCase
    @Inject lateinit var ensureCurrentUserRow: EnsureCurrentUserRowUseCase

    /** Purges the partner replica when this user's couple dissolves, on either side (ADR-0008). */
    @Inject lateinit var watchUnpair: WatchUnpairUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IponTheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                val status by authViewModel.status.collectAsState()
                when (val current = status) {
                    is AuthStatus.Authenticated -> {
                        LaunchedEffect(current.userId) {
                            ensureCurrentUserRow()
                            materializeRecurringRules()
                            // Enqueue a background sync on network reconnect (ADR-0012).
                            // KEEP_EXISTING so simultaneous triggers coalesce to one worker.
                            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                                SyncWorker.WORK_NAME,
                                ExistingWorkPolicy.KEEP,
                                SyncWorker.buildRequest(),
                            )
                        }
                        // Separate collector: runs for the whole session, purging the partner
                        // replica if the couple is dissolved from either side (ADR-0008).
                        LaunchedEffect(current.userId) { watchUnpair() }
                        IponApp(onSignOut = authViewModel::signOut)
                    }

                    AuthStatus.Unauthenticated -> AuthScreen(viewModel = authViewModel)
                    AuthStatus.Loading -> SplashScreen()
                }
            }
        }
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
