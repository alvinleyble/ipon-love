package com.iponlove.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.iponlove.app.core.sync.PreSyncStep
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.core.sync.data.ClockOffsetStore
import com.iponlove.app.core.sync.data.DataStoreSyncCursorStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.time.OffsetDateTime
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
    @Singleton
    fun syncEngine(
        syncers: Set<@JvmSuppressWildcards TableSyncer>,
        preSyncSteps: Set<@JvmSuppressWildcards PreSyncStep>,
        clock: SyncClock,
        clockOffsetStore: ClockOffsetStore,
        client: SupabaseClient,
    ): SyncEngine = SyncEngine(
        syncers = syncers,
        preSyncSteps = preSyncSteps,
        clock = clock,
        clockOffsetStore = clockOffsetStore,
        serverTimeFetcher = {
            val raw = client.postgrest.rpc("get_server_time").decodeAs<String>()
            OffsetDateTime.parse(raw).toInstant()
        },
    )
}

/** Declares multibinding sets so they resolve even with zero contributions. */
@Module
@InstallIn(SingletonComponent::class)
interface SyncMultibindsModule {
    @Multibinds
    fun tableSyncers(): Set<TableSyncer>

    @Multibinds
    fun preSyncSteps(): Set<PreSyncStep>
}
