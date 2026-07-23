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
import com.iponlove.app.feature.transactions.domain.usecase.CleanupOrphanedReceiptsUseCase
import com.iponlove.app.feature.widget.data.WidgetSessionHintWriter
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
    @Inject lateinit var widgetSessionHintWriter: WidgetSessionHintWriter
    @Inject lateinit var cleanupOrphanedReceipts: CleanupOrphanedReceiptsUseCase

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
        // Mirror the session state into a fast local hint so the balance widget never blocks on the
        // Supabase SDK's cold-start session read (Item 36).
        widgetSessionHintWriter.start(appScope)
        // Sweep filesDir/receipts for compressed files that never got a transaction_images row
        // (abandoned editor, or picked-then-removed before save — Item 14).
        appScope.launch { cleanupOrphanedReceipts() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
