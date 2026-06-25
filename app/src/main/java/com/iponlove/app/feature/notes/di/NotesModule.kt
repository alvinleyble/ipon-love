package com.iponlove.app.feature.notes.di

import com.iponlove.app.core.sync.PreSyncStep
import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.notes.data.NoteAttachmentRepositoryImpl
import com.iponlove.app.feature.notes.data.NoteRepositoryImpl
import com.iponlove.app.feature.notes.data.remote.NoteAttachmentRemoteSource
import com.iponlove.app.feature.notes.data.remote.NoteRemoteSource
import com.iponlove.app.feature.notes.data.remote.SupabaseNoteAttachmentRemoteSource
import com.iponlove.app.feature.notes.data.remote.SupabaseNoteRemoteSource
import com.iponlove.app.feature.notes.data.sync.NoteAttachmentTableSyncer
import com.iponlove.app.feature.notes.data.sync.NoteTableSyncer
import com.iponlove.app.feature.notes.data.sync.PartnerNoteAttachmentTableSyncer
import com.iponlove.app.feature.notes.data.sync.PartnerNoteTableSyncer
import com.iponlove.app.feature.notes.data.upload.NoteAttachmentUploader
import com.iponlove.app.feature.notes.domain.repository.NoteAttachmentRepository
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

    @Binds
    fun noteAttachmentRepository(impl: NoteAttachmentRepositoryImpl): NoteAttachmentRepository

    @Binds
    @Singleton
    fun noteRemoteSource(impl: SupabaseNoteRemoteSource): NoteRemoteSource

    @Binds
    @Singleton
    fun noteAttachmentRemoteSource(impl: SupabaseNoteAttachmentRemoteSource): NoteAttachmentRemoteSource

    @Binds
    @IntoSet
    fun noteTableSyncer(impl: NoteTableSyncer): TableSyncer

    @Binds
    @IntoSet
    fun partnerNoteTableSyncer(impl: PartnerNoteTableSyncer): TableSyncer

    @Binds
    @IntoSet
    fun noteAttachmentTableSyncer(impl: NoteAttachmentTableSyncer): TableSyncer

    @Binds
    @IntoSet
    fun partnerNoteAttachmentTableSyncer(impl: PartnerNoteAttachmentTableSyncer): TableSyncer

    @Binds
    @IntoSet
    fun noteAttachmentUploader(impl: NoteAttachmentUploader): PreSyncStep
}
