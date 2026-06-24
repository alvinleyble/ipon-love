package com.iponlove.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.iponlove.app.core.ui.theme.IponTheme
import com.iponlove.app.feature.recurring.domain.usecase.MaterializeRecurringRulesUseCase
import com.iponlove.app.navigation.IponApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Generates any recurring transactions that came due while the app was closed (ADR-0012:
     *  interactive catch-up runs in-process; WorkManager owns background retry later). */
    @Inject
    lateinit var materializeRecurringRules: MaterializeRecurringRulesUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { materializeRecurringRules() }
        enableEdgeToEdge()
        setContent {
            IponTheme {
                IponApp()
            }
        }
    }
}
