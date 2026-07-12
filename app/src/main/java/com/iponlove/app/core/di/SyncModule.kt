package com.iponlove.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.iponlove.app.core.sync.CoupleBell
import com.iponlove.app.core.sync.CoupleChannelManager
import com.iponlove.app.core.sync.FullSyncStep
import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.core.sync.PreSyncStep
import com.iponlove.app.core.sync.RoomTransactionRunner
import com.iponlove.app.core.sync.SupabaseCoupleBell
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.core.sync.data.ClockOffsetStore
import com.iponlove.app.core.sync.data.DataStoreSyncCursorStore
import com.iponlove.app.core.database.IponDatabase
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.OffsetDateTime
import javax.inject.Qualifier
import javax.inject.Singleton

/** Single DataStore holding sync bookkeeping: per-table cursors + the clock offset. */
private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

/**
 * Provides the entity-agnostic sync core. Feature modules contribute their per-table
 * [TableSyncer] via `@Binds @IntoSet`; the engine sorts them into FK order itself, so an
 * empty set (no features wired yet) is valid and yields a no-op sync.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun syncDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.syncDataStore

    @Provides
    @Singleton
    fun syncClock(): SyncClock = SyncClock()

    @Provides
    @Singleton
    fun cursorStore(dataStore: DataStore<Preferences>): SyncCursorStore =
        DataStoreSyncCursorStore(dataStore)

    @Provides
    @Singleton
    fun clockOffsetStore(dataStore: DataStore<Preferences>): ClockOffsetStore =
        ClockOffsetStore(dataStore)

    @Provides
    fun localTransactionRunner(database: IponDatabase): LocalTransactionRunner =
        RoomTransactionRunner(database)

    @Provides
    @Singleton
    fun syncEngine(
        syncers: Set<@JvmSuppressWildcards TableSyncer>,
        preSyncSteps: Set<@JvmSuppressWildcards PreSyncStep>,
        fullSyncSteps: Set<@JvmSuppressWildcards FullSyncStep>,
        clock: SyncClock,
        clockOffsetStore: ClockOffsetStore,
        client: SupabaseClient,
    ): SyncEngine = SyncEngine(
        syncers = syncers,
        preSyncSteps = preSyncSteps,
        fullSyncSteps = fullSyncSteps,
        clock = clock,
        clockOffsetStore = clockOffsetStore,
        serverTimeFetcher = {
            val raw = client.postgrest.rpc("get_server_time").decodeAs<String>()
            OffsetDateTime.parse(raw).toInstant()
        },
    )

    /**
     * Process-lifetime scope for the live-sync collectors and the Realtime websocket
     * (ADR-0015). [SupervisorJob] so one failing collector can't tear down the others;
     * [Dispatchers.Default] since the work is debounce + suspend I/O, not UI.
     */
    @Provides
    @Singleton
    @LiveSyncScope
    fun liveSyncScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun coupleBell(
        client: SupabaseClient,
        @LiveSyncScope scope: CoroutineScope,
    ): CoupleBell = SupabaseCoupleBell(client, scope)

    @Provides
    @Singleton
    fun coupleChannelManager(
        bell: CoupleBell,
        engine: SyncEngine,
        syncTrigger: SyncTrigger,
        observePairingState: ObservePairingStateUseCase,
        @LiveSyncScope scope: CoroutineScope,
    ): CoupleChannelManager = CoupleChannelManager(
        bell = bell,
        engine = engine,
        syncTrigger = syncTrigger,
        // Lazily built per (re)subscription so its eager user-id read happens only post-auth.
        pairingStates = { observePairingState() },
        scope = scope,
    )
}

/** Qualifies the process-lifetime [CoroutineScope] that drives live sync (ADR-0015). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LiveSyncScope

/** Declares multibinding sets so they resolve even with zero contributions. */
@Module
@InstallIn(SingletonComponent::class)
interface SyncMultibindsModule {
    @Multibinds
    fun tableSyncers(): Set<TableSyncer>

    @Multibinds
    fun preSyncSteps(): Set<PreSyncStep>

    @Multibinds
    fun fullSyncSteps(): Set<FullSyncStep>
}
