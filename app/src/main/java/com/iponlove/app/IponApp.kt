package com.iponlove.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.data.ClockOffsetStore
import com.iponlove.app.feature.budgets.presentation.BudgetAlertNotifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hilt application entry point. Also provides the WorkManager configuration so
 * background sync workers can be Hilt-injected once the sync layer lands.
 */
@HiltAndroidApp
class IponApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncClock: SyncClock
    @Inject lateinit var clockOffsetStore: ClockOffsetStore
    @Inject lateinit var budgetAlertNotifier: BudgetAlertNotifier

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        appScope.launch { clockOffsetStore.restoreInto(syncClock) }
        budgetAlertNotifier.createChannel()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
