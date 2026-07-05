package com.iponlove.app.feature.tutorial.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Seen-tour set read/write + the v1.6.1 single-boolean legacy migration (ADR-0038 dec. 2/5). */
class TutorialRepositoryImplTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun repo(name: String = "tutorial.preferences_pb") =
        TutorialRepositoryImpl(PreferenceDataStoreFactory.create { tmpFolder.newFile(name) })

    @Test
    fun freshInstall_seenSetIsEmpty() = runTest {
        assertThat(repo().observeSeenTours().first()).isEmpty()
    }

    @Test
    fun markTourSeen_addsToSet_andIsAdditive() = runTest {
        val repo = repo()
        repo.markTourSeen(TutorialTours.RECORDS)
        repo.markTourSeen(TutorialTours.ANALYSIS)

        assertThat(repo.observeSeenTours().first())
            .containsExactly(TutorialTours.RECORDS, TutorialTours.ANALYSIS)
    }

    @Test
    fun legacyBooleanTrue_seedsShellOnly_soExistingTestersDontRe_seeShell() = runTest {
        val ds = PreferenceDataStoreFactory.create { tmpFolder.newFile("legacy.preferences_pb") }
        // Simulate a v1.6.1 upgrade: the old single boolean was set, the new set never written.
        ds.edit { it[booleanPreferencesKey("tutorial_seen")] = true }

        val seen = TutorialRepositoryImpl(ds).observeSeenTours().first()

        assertThat(seen).containsExactly(TutorialTours.SHELL)
    }

    @Test
    fun legacyMigration_survivesFirstMarkSeen() = runTest {
        val ds = PreferenceDataStoreFactory.create { tmpFolder.newFile("legacy2.preferences_pb") }
        ds.edit { it[booleanPreferencesKey("tutorial_seen")] = true }
        val repo = TutorialRepositoryImpl(ds)

        // Writing the explicit set must not drop the shell ID the upgrade earned via the boolean.
        repo.markTourSeen(TutorialTours.RECORDS)

        assertThat(repo.observeSeenTours().first())
            .containsExactly(TutorialTours.SHELL, TutorialTours.RECORDS)
    }

    @Test
    fun clearAllTours_emptiesSet_andDropsLegacyFlag() = runTest {
        val ds = PreferenceDataStoreFactory.create { tmpFolder.newFile("clear.preferences_pb") }
        ds.edit { it[booleanPreferencesKey("tutorial_seen")] = true }
        val repo = TutorialRepositoryImpl(ds)
        repo.markTourSeen(TutorialTours.RECORDS)

        repo.clearAllTours()

        // Legacy flag gone too, else the shell would re-seed and Replay wouldn't restart the shell.
        assertThat(repo.observeSeenTours().first()).isEmpty()
    }
}
