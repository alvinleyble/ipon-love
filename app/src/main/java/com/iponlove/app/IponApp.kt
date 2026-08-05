package com.iponlove.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.iponlove.app.core.sync.CoupleChannelManager
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.data.ClockOffsetStore
import com.iponlove.app.feature.export.data.ExportFileWriter
import com.iponlove.app.feature.notifications.presentation.SystemNotificationPresenter
import com.iponlove.app.feature.transactions.data.ReceiptScanFileStore
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
    @Inject lateinit var notificationPresenter: SystemNotificationPresenter
    @Inject lateinit var coupleChannelManager: CoupleChannelManager
    @Inject lateinit var widgetSessionHintWriter: WidgetSessionHintWriter
    @Inject lateinit var cleanupOrphanedReceipts: CleanupOrphanedReceiptsUseCase
    @Inject lateinit var exportFileWriter: ExportFileWriter
    @Inject lateinit var receiptScanFileStore: ReceiptScanFileStore

    // Coil asks for this lazily on first image load; every AsyncImage in the app then goes
    // through the auth-attaching loader (private Storage buckets — see StorageAuthInterceptor).
    @Inject lateinit var coilImageLoader: dagger.Lazy<ImageLoader>

    override fun newImageLoader(): ImageLoader = coilImageLoader.get()

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        appScope.launch { clockOffsetStore.restoreInto(syncClock) }
        // One OS channel per notification category (ADR-0053) — registered up front so a channel
        // exists before its first post, whichever category produces first.
        notificationPresenter.createChannels()
        // Launch the live-sync collectors once per process. They idle (no socket, no push)
        // until MainActivity reports foreground + an authenticated, paired user (ADR-0015).
        coupleChannelManager.start()
        // Mirror the session state into a fast local hint so the balance widget never blocks on the
        // Supabase SDK's cold-start session read (Item 36).
        widgetSessionHintWriter.start(appScope)
        // Sweep filesDir/receipts for compressed files that never got a transaction_images row
        // (abandoned editor, or picked-then-removed before save — Item 14).
        appScope.launch { cleanupOrphanedReceipts() }
        // Clear any leftover temp export files — a share is a transmission, not a stored doc (Item 6).
        appScope.launch { exportFileWriter.sweep() }
        // Age-based sweep of abandoned cacheDir/scans captures — never unconditional, so it can't
        // delete an in-flight capture redelivered after a process death behind the camera
        // (v1.7.3 Item 2, ADR-0062 decision 9).
        appScope.launch { receiptScanFileStore.sweep() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
