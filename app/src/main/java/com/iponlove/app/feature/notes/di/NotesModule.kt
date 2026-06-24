package com.iponlove.app.feature.notes.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.notes.data.NoteRepositoryImpl
import com.iponlove.app.feature.notes.data.remote.NoteRemoteSource
import com.iponlove.app.feature.notes.data.remote.SupabaseNoteRemoteSource
import com.iponlove.app.feature.notes.data.sync.NoteTableSyncer
import com.iponlove.app.feature.notes.data.sync.PartnerNoteTableSyncer
import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface NotesModule {

    @Binds
    fun noteRepository(impl: NoteRepositoryImpl): NoteRepository

    // No-op stub until the Supabase backend slice provides the real remote source.
    @Binds
    @Singleton
    fun noteRemoteSource(impl: SupabaseNoteRemoteSource): NoteRemoteSource

    /** Contributes notes to the sync engine's table set; engine sorts into FK order. */
    @Binds
    @IntoSet
    fun noteTableSyncer(impl: NoteTableSyncer): TableSyncer

    /** Contributes the partner-notes replica pull (ADR-0004/0005). */
    @Binds
    @IntoSet
    fun partnerNoteTableSyncer(impl: PartnerNoteTableSyncer): TableSyncer
}
