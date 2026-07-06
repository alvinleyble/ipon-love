package com.iponlove.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.iponlove.app.core.sync.CoupleChannelManager
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
class IponApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncClock: SyncClock
    @Inject lateinit var clockOffsetStore: ClockOffsetStore
    @Inject lateinit var budgetAlertNotifier: BudgetAlertNotifier
    @Inject lateinit var coupleChannelManager: CoupleChannelManager

    // Coil asks for this lazily on first image load; every AsyncImage in the app then goes
    // through the auth-attaching loader (private Storage buckets — see StorageAuthInterceptor).
    @Inject lateinit var coilImageLoader: dagger.Lazy<ImageLoader>

    override fun newImageLoader(): ImageLoader = coilImageLoader.get()

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        appScope.launch { clockOffsetStore.restoreInto(syncClock) }
        budgetAlertNotifier.createChannel()
        // Launch the live-sync collectors once per process. They idle (no socket, no push)
        // until MainActivity reports foreground + an authenticated, paired user (ADR-0015).
        coupleChannelManager.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
