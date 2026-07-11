package com.iponlove.app.feature.accounts

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.accounts.data.AccountRepositoryImpl
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * The repository is the single enforcement point for the sync write rules
 * (ADR-0001 stamping, ADR-0002 dirty flag, ADR-0010 soft delete).
 */
class AccountRepositoryImplTest {

    private val dao = FakeAccountDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val repository = AccountRepositoryImpl(dao, clock, currentUser)

    private fun newAccount(id: String, name: String = "GCash") = Account(
        id = id,
        name = name,
        type = AccountType.EWALLET,
        openingBalance = BigDecimal("500.00"),
    )

    @Test
    fun upsert_newAccount_stampsOwnerAndSyncColumns() = runTest {
        repository.upsertAccount(newAccount("a"))

        val row = dao.store.getValue("a")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.pendingSync).isTrue()
        assertThat(row.isDeleted).isFalse()
        assertThat(row.serverRev).isNull()
        assertThat(row.updatedAt).isEqualTo(now)
        assertThat(row.createdAt).isEqualTo(now)
    }

    @Test
    fun upsert_existingAccount_advancesUpdatedAtMonotonically_andPreservesProvenance() = runTest {
        // Seed a server-synced row directly in the dao.
        dao.store["a"] = accountEntity(
            id = "a",
            userId = "owner-x",
            createdAt = Instant.ofEpochMilli(1_000),
            updatedAt = Instant.ofEpochMilli(10_000),
            serverRev = 55,
            pendingSync = false,
        )
        // Wall clock has not advanced past the existing stamp.
        now = Instant.ofEpochMilli(10_000)

        repository.upsertAccount(newAccount("a", name = "GCash Renamed"))

        val row = dao.store.getValue("a")
        assertThat(row.name).isEqualTo("GCash Renamed")
        assertThat(row.pendingSync).isTrue()
        // Monotonic: max(now, prev + 1ms) = prev + 1ms.
        assertThat(row.updatedAt).isEqualTo(Instant.ofEpochMilli(10_001))
        // Provenance survives an edit.
        assertThat(row.userId).isEqualTo("owner-x")
        assertThat(row.createdAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(row.serverRev).isEqualTo(55)
    }

    @Test
    fun setArchived_flagsAndMarksDirty() = runTest {
        dao.store["a"] = accountEntity(id = "a", updatedAt = Instant.ofEpochMilli(1_000))

        repository.setArchived("a", archived = true)

        val row = dao.store.getValue("a")
        assertThat(row.isArchived).isTrue()
        assertThat(row.pendingSync).isTrue()
        assertThat(row.updatedAt).isEqualTo(now)
    }

    @Test
    fun delete_isSoft_setsTombstoneAndMarksDirty() = runTest {
        dao.store["a"] = accountEntity(id = "a", serverRev = 3, updatedAt = Instant.ofEpochMilli(1_000))

        repository.deleteAccount("a")

        val row = dao.store.getValue("a")
        assertThat(row.isDeleted).isTrue()
        assertThat(row.pendingSync).isTrue()
        // Tombstone keeps its identity so the delete can sync (ADR-0010).
        assertThat(row.serverRev).isEqualTo(3)
    }

    @Test
    fun observeAccounts_hidesDeleted_andMapsToDomain() = runTest {
        dao.store["a"] = accountEntity(id = "a", name = "Cash", position = 0)
        dao.store["b"] = accountEntity(id = "b", name = "Gone", position = 1, isDeleted = true)

        val accounts = repository.observeAccounts().first()

        assertThat(accounts.map { it.name }).containsExactly("Cash")
    }

    // ---- manual reorder (item 9b) -----------------------------------------------------

    @Test
    fun reorderAccounts_writesIndexAsPosition_andMarksOnlyChangedRowsDirty() = runTest {
        dao.store["a"] = accountEntity(id = "a", position = 0, updatedAt = Instant.ofEpochMilli(1_000))
        dao.store["b"] = accountEntity(id = "b", position = 1, updatedAt = Instant.ofEpochMilli(1_000))
        dao.store["c"] = accountEntity(id = "c", position = 2, updatedAt = Instant.ofEpochMilli(1_000))

        repository.reorderAccounts(listOf("c", "a", "b"))

        assertThat(dao.store.getValue("c").position).isEqualTo(0)
        assertThat(dao.store.getValue("a").position).isEqualTo(1)
        assertThat(dao.store.getValue("b").position).isEqualTo(2)
        // "a" moved from index 0 to 1, "b" from 1 to 2 — dirtied and re-stamped.
        assertThat(dao.store.getValue("a").pendingSync).isTrue()
        assertThat(dao.store.getValue("a").updatedAt).isEqualTo(now)
        assertThat(dao.store.getValue("b").pendingSync).isTrue()
        // "c" moved from index 2 to 0 — also changed.
        assertThat(dao.store.getValue("c").pendingSync).isTrue()
    }

    @Test
    fun reorderAccounts_skipsRowsWhosePositionIsUnchanged() = runTest {
        dao.store["a"] = accountEntity(id = "a", position = 0, updatedAt = Instant.ofEpochMilli(1_000), pendingSync = false)
        dao.store["b"] = accountEntity(id = "b", position = 1, updatedAt = Instant.ofEpochMilli(1_000), pendingSync = false)

        repository.reorderAccounts(listOf("a", "b"))

        // Order matches current positions exactly — nothing should be re-stamped or dirtied.
        assertThat(dao.store.getValue("a").pendingSync).isFalse()
        assertThat(dao.store.getValue("a").updatedAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(dao.store.getValue("b").pendingSync).isFalse()
    }

    @Test
    fun reorderAccounts_ignoresUnknownIds() = runTest {
        dao.store["a"] = accountEntity(id = "a", position = 0)

        repository.reorderAccounts(listOf("ghost", "a"))

        assertThat(dao.store.getValue("a").position).isEqualTo(1)
        assertThat(dao.store.keys).containsExactly("a")
    }

    // ---- shared accounts (ADR-0018) -------------------------------------------------

    @Test
    fun upsert_newAccount_stampsCreatedBy() = runTest {
        repository.upsertAccount(newAccount("a"))

        assertThat(dao.store.getValue("a").createdBy).isEqualTo("user-1")
    }

    @Test
    fun shareAccount_makesCoupleOwned_nullingUserId_keepingCreator() = runTest {
        dao.store["a"] = accountEntity(id = "a", userId = "user-1", createdBy = "user-1")

        repository.shareAccount("a", coupleId = "couple-1")

        val row = dao.store.getValue("a")
        assertThat(row.userId).isNull()
        assertThat(row.coupleId).isEqualTo("couple-1")
        assertThat(row.createdBy).isEqualTo("user-1")
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun shareAccount_backfillsCreatorFromUserId_whenMissing() = runTest {
        dao.store["a"] = accountEntity(id = "a", userId = "user-1", createdBy = null)

        repository.shareAccount("a", coupleId = "couple-1")

        assertThat(dao.store.getValue("a").createdBy).isEqualTo("user-1")
    }

    @Test
    fun unshareAccount_revertsToCreator() = runTest {
        // Shared account created by user-1, currently couple-owned.
        dao.store["a"] = accountEntity(
            id = "a", userId = null, coupleId = "couple-1", createdBy = "user-1",
        )

        repository.unshareAccount("a")

        val row = dao.store.getValue("a")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.coupleId).isNull()
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun unshareAccount_byNonCreator_isNoOp() = runTest {
        // Creator-only un-share (ADR-0018, v1.6.5 Item 20): user-1 tries to un-share an account
        // the *other* member (owner-2) created. Reverting would stamp owner-2's user_id onto a
        // row user-1 cannot push — RLS-rejected, wedging sync — so it must be a no-op instead.
        val original = accountEntity(
            id = "a", userId = null, coupleId = "couple-1", createdBy = "owner-2",
        )
        dao.store["a"] = original

        repository.unshareAccount("a")

        // Row untouched: still couple-owned, not marked dirty.
        assertThat(dao.store.getValue("a")).isEqualTo(original)
    }

    @Test
    fun purgePartnerData_revertsMine_deletesPartnersCoupleRows_andPartnerReplicas() = runTest {
        // Mine, couple-owned → reverts to my personal account, kept.
        dao.store["mine"] = accountEntity(
            id = "mine", userId = null, coupleId = "couple-1", createdBy = "user-1",
        )
        // Partner-created couple-owned → deleted (the partner keeps it via their own revert).
        dao.store["theirs"] = accountEntity(
            id = "theirs", userId = null, coupleId = "couple-1", createdBy = "owner-2",
        )
        // Replicated partner personal account → deleted.
        dao.store["replica"] = accountEntity(id = "replica", userId = "owner-2", coupleId = null)
        // My personal account → untouched.
        dao.store["personal"] = accountEntity(id = "personal", userId = "user-1", coupleId = null)

        repository.purgePartnerData()

        assertThat(dao.store.keys).containsExactly("mine", "personal")
        val mine = dao.store.getValue("mine")
        assertThat(mine.userId).isEqualTo("user-1")
        assertThat(mine.coupleId).isNull()
    }
}
