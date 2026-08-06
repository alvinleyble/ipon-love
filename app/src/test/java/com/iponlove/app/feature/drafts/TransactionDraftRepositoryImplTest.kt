package com.iponlove.app.feature.drafts

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.drafts.data.TransactionDraftRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class TransactionDraftRepositoryImplTest {

    private val dao = FakeTransactionDraftDao()
    private var now = Instant.parse("2026-08-06T10:00:00Z")
    private val clock = SyncClock(now = { now })
    private val repo = TransactionDraftRepositoryImpl(
        dao = dao,
        clock = clock,
        currentUser = CurrentUserProvider { "user-1" },
    )

    // ---- sync bookkeeping (ADR-0001 / 0002 / 0010) ----

    @Test
    fun saveDraft_stampsOwnership_updatedAt_andThePendingFlag() = runTest {
        repo.saveDraft(draft(id = "d1"))

        val row = dao.store.getValue("d1")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.updatedAt).isEqualTo(now)
        assertThat(row.pendingSync).isTrue()
        assertThat(row.isDeleted).isFalse()
    }

    /** ADR-0001: a backward wall-clock jump must never let a newer edit lose to its own prior row. */
    @Test
    fun saveDraft_keepsUpdatedAtMonotonic() = runTest {
        repo.saveDraft(draft(id = "d1"))
        val first = dao.store.getValue("d1").updatedAt
        now = now.minusSeconds(60)

        repo.saveDraft(draft(id = "d1", amount = BigDecimal("9.00")))

        assertThat(dao.store.getValue("d1").updatedAt).isGreaterThan(first)
    }

    /** The age label's whole point is showing how long something has sat; re-parking mustn't reset it. */
    @Test
    fun saveDraft_preservesTheOriginalParkedTimestampAcrossReParks() = runTest {
        repo.saveDraft(draft(id = "d1", parkedAt = Instant.ofEpochMilli(1_000)))

        repo.saveDraft(draft(id = "d1", parkedAt = Instant.ofEpochMilli(999_000)))

        assertThat(dao.store.getValue("d1").createdAt).isEqualTo(Instant.ofEpochMilli(1_000))
    }

    @Test
    fun deleteDraft_isSoft_andReturnsTheFilesItReleased() = runTest {
        repo.saveDraft(draft(id = "d1", localImageIds = listOf("img-1", "img-2"), receiptCount = 2))

        val released = repo.deleteDraft("d1")

        assertThat(released).containsExactly("img-1", "img-2")
        val row = dao.store.getValue("d1")
        assertThat(row.isDeleted).isTrue()
        assertThat(row.pendingSync).isTrue()
        // The tombstone stops holding files hostage: nothing points at them any more, so the
        // sweep is free to collect whatever the caller didn't delete.
        assertThat(row.localImageIds).isEmpty()
    }

    @Test
    fun deleteDraft_onAnUnknownId_isANoOp() = runTest {
        assertThat(repo.deleteDraft("nope")).isEmpty()
        assertThat(dao.store).isEmpty()
    }

    // ---- promotion (ADR-0066 decision 5) ----

    @Test
    fun retireDraft_softDeletesTheRow() = runTest {
        repo.saveDraft(draft(id = "d1"))

        repo.retireDraft("d1")

        assertThat(dao.store.getValue("d1").isDeleted).isTrue()
        assertThat(dao.store.getValue("d1").pendingSync).isTrue()
    }

    /**
     * The safety property the whole promotion design rests on: re-settling an already-promoted
     * draft must do nothing at all, so a failed retire costs one extra tap and never doubles money.
     */
    @Test
    fun retireDraft_isIdempotent_andANoOpForATransactionThatWasNeverParked() = runTest {
        repo.saveDraft(draft(id = "d1"))
        repo.retireDraft("d1")
        val afterFirst = dao.store.getValue("d1")

        repo.retireDraft("d1")
        repo.retireDraft("never-parked")

        assertThat(dao.store.getValue("d1")).isEqualTo(afterFirst)
        assertThat(dao.store).doesNotContainKey("never-parked")
    }

    // ---- reads ----

    @Test
    fun observeDrafts_showsOwnActiveDraftsOldestFirst() = runTest {
        repo.saveDraft(draft(id = "old", parkedAt = Instant.ofEpochMilli(1_000)))
        repo.saveDraft(draft(id = "new", parkedAt = Instant.ofEpochMilli(9_000)))
        repo.saveDraft(draft(id = "gone"))
        repo.deleteDraft("gone")
        dao.store["partner"] = draftEntity("partner", userId = "user-2")

        assertThat(repo.observeDrafts().first().map { it.id }).containsExactly("old", "new").inOrder()
        assertThat(repo.observeDraftCount().first()).isEqualTo(2)
    }

    @Test
    fun getDraft_doesNotReturnARetiredRow() = runTest {
        repo.saveDraft(draft(id = "d1"))
        repo.retireDraft("d1")

        assertThat(repo.getDraft("d1")).isNull()
    }

    /** The union the orphaned-receipt sweep consumes (decision 6). */
    @Test
    fun allLocalImageIds_coversActiveDraftsOnly() = runTest {
        repo.saveDraft(draft(id = "d1", localImageIds = listOf("img-1")))
        repo.saveDraft(draft(id = "d2", localImageIds = listOf("img-2", "img-3")))
        repo.saveDraft(draft(id = "d3", localImageIds = listOf("img-4")))
        repo.deleteDraft("d3")

        assertThat(repo.allLocalImageIds()).containsExactly("img-1", "img-2", "img-3")
    }

    /**
     * A draft pulled from another device carries no local ids. Applying it must not wipe the
     * association this device already holds, or the photos it is keeping become orphans.
     */
    @Test
    fun applyPullBatch_preservesThisDevicesOwnLocalImageIds() = runTest {
        repo.saveDraft(draft(id = "d1", localImageIds = listOf("img-1"), receiptCount = 1))

        dao.applyPullBatch(listOf(draftEntity("d1", localImageIds = emptyList(), note = "edited")))

        val row = dao.store.getValue("d1")
        assertThat(row.note).isEqualTo("edited")
        assertThat(row.localImageIds).containsExactly("img-1")
    }
}
