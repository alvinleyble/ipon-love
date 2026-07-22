package com.iponlove.app.feature.accounts.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.LastActiveUserStore
import com.iponlove.app.core.session.userIdOrNull
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.accounts.data.local.AccountDao
import com.iponlove.app.feature.accounts.data.local.AccountEntity
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [AccountRepository]. The single place every account write applies the
 * sync bookkeeping: each mutation stamps a fresh monotonic `updated_at` (ADR-0001) and
 * raises `pending_sync` (ADR-0002); deletes are soft (ADR-0010).
 */
class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
    private val lastActiveUser: LastActiveUserStore = LastActiveUserStore.NONE,
) : AccountRepository {

    // userId resolved inside the flow, not eagerly: re-collected during the sign-out
    // transition (auth already null) where an eager userId() would crash the process.
    // Fall back to the persisted last-active id (Item 10 follow-up): a cold/backgrounded
    // process the home-screen widget revives has real local rows but no restored Supabase
    // session yet — the id is a *local read scope*, not an auth check, so reading it from the
    // durable store lets the widget paint real data without waiting on the network session.
    // Safe in-app: currentUserOrNull() is null there only pre-mount (no observer composed) or
    // during sign-out (Room already wiped → still empty). Writes keep the strict userId().
    override fun observeAccounts(includeArchived: Boolean): Flow<List<Account>> = flow {
        val userId = currentUser.userIdOrNull() ?: lastActiveUser.lastUserId()
        if (userId == null) emit(emptyList())
        else emitAll(dao.observeAccounts(userId, includeArchived).map { rows -> rows.map { it.toDomain(userId) } })
    }

    override suspend fun getAccount(id: String): Account? = dao.getById(id)?.toDomain(currentUser.userIdOrNull())

    override suspend fun countOwnedAccounts(): Int = dao.countOwned(currentUser.userId())

    override suspend fun countSharedAccounts(): Int = dao.countShared()

    override suspend fun upsertAccount(account: Account) {
        val existing = dao.getById(account.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        val me = currentUser.userId()
        dao.upsert(
            AccountEntity(
                id = account.id,
                // Ownership (personal vs. couple-owned) is managed by share/unshare, never the
                // editor — so it survives edits untouched. New rows are personal, owned by me.
                userId = if (existing != null) existing.userId else me,
                coupleId = existing?.coupleId,
                createdBy = existing?.createdBy ?: me,
                name = account.name,
                type = account.type,
                openingBalance = account.openingBalance,
                icon = account.icon,
                color = account.color,
                position = account.position,
                isArchived = account.isArchived,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun reorderAccounts(orderedIds: List<String>) {
        var changed = false
        orderedIds.forEachIndexed { index, id ->
            val existing = dao.getById(id) ?: return@forEachIndexed
            if (existing.position == index) return@forEachIndexed
            dao.upsert(
                existing.copy(
                    position = index,
                    updatedAt = clock.stamp(existing.updatedAt),
                    pendingSync = true,
                ),
            )
            changed = true
        }
        if (changed) syncTrigger.requestPush()
    }

    override suspend fun shareAccount(id: String, coupleId: String) {
        val existing = dao.getById(id) ?: return
        if (existing.coupleId != null) return // already shared
        dao.upsert(
            existing.copy(
                // Couple-owned: schema's owner check requires user_id null when couple_id set.
                userId = null,
                coupleId = coupleId,
                createdBy = existing.createdBy ?: existing.userId,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun unshareAccount(id: String) {
        val existing = dao.getById(id) ?: return
        // Creator-only (ADR-0018, v1.6.5 Item 20): a shared account reverts to its creator, so a
        // non-creator un-sharing would stamp the row with the *other* partner's user_id — a row
        // RLS forbids this device to push, wedging every pending accounts row. The UI already
        // hides "Make personal" for non-creators; this is the defence-in-depth backstop (mirrors
        // goals' owner-only mutateOwn). Null createdBy (legacy shared row) is nobody's to un-share.
        if (existing.createdBy != currentUser.userId()) return
        val creator = existing.createdBy ?: existing.userId ?: return
        dao.upsert(
            existing.copy(
                userId = creator,
                coupleId = null,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                isArchived = archived,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun deleteAccount(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun purgePartnerData() {
        val me = currentUser.userId()
        // Revert my shared accounts to personal (keep them) before deleting the partner's
        // couple-owned rows and replicated partner personal rows (ADR-0008/0018). Order matters:
        // the revert clears coupleId on my rows so the couple-delete can't catch them.
        dao.revertOwnCoupleRowsToCreator(me, clock.stamp(null).toEpochMilli())
        dao.deleteCoupleRowsNotCreatedBy(me)
        dao.deleteNotOwnedBy(me)
    }
}
