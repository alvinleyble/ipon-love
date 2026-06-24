package com.iponlove.app.feature.notes

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import com.iponlove.app.feature.notes.domain.usecase.UpsertNoteUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UpsertNoteUseCaseTest {

    private class RecordingRepository : NoteRepository {
        var saved: Note? = null
        override fun observeNotes(): Flow<List<Note>> = throw UnsupportedOperationException()
        override suspend fun getNote(id: String): Note? = null
        override suspend fun upsertNote(note: Note) { saved = note }
        override suspend fun deleteNote(id: String) = Unit
        override suspend fun purgePartnerData() = Unit
    }

    private val repository = RecordingRepository()
    private val upsert = UpsertNoteUseCase(repository)

    @Test
    fun trimsTitle_keepsBodyAsIs() = runTest {
        upsert(note("n", title = "  Trip  ", contentHtml = "<p>  body  </p>"))

        assertThat(repository.saved?.title).isEqualTo("Trip")
        assertThat(repository.saved?.contentHtml).isEqualTo("<p>  body  </p>")
    }

    @Test
    fun allowsBlankTitle_bodyCarriesTheNote() = runTest {
        upsert(note("n", title = "   ", contentHtml = "<p>note</p>"))

        // No exception, and the (now empty) title is persisted — unlike categories.
        assertThat(repository.saved?.title).isEmpty()
    }
}
