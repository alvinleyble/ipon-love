package com.iponlove.app.feature.accounts.data.sync

import com.iponlove.app.core.sync.BasePartnerTableSyncer
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.accounts.data.local.AccountDao
import com.iponlove.app.feature.accounts.data.local.AccountEntity
import com.iponlove.app.feature.accounts.data.remote.AccountRemoteSource
import com.iponlove.app.feature.accounts.data.toEntity
import javax.inject.Inject

/**
 * Replicates the partner's accounts from the `partner_accounts` view into the shared
 * `accounts` Room table (ADR-0004/0005). A deleted partner account crosses the view with
 * content nulled → purged locally; a visible one is upserted.
 */
class PartnerAccountTableSyncer @Inject constructor(
    private val dao: AccountDao,
    private val remote: AccountRemoteSource,
    cursors: SyncCursorStore,
) : BasePartnerTableSyncer<AccountEntity>(SyncTable.PARTNER_ACCOUNTS, cursors) {

    override suspend fun remotePullPartner(cursor: Long, limit: Int): List<AccountEntity> =
        remote.pullPartner(cursor, limit).map { it.toEntity() }

    override fun shouldPurge(row: AccountEntity): Boolean = row.isDeleted
    override suspend fun hardDelete(id: String) = dao.deleteById(id)
    override suspend fun applyPullBatch(rows: List<AccountEntity>) = dao.applyPullBatch(rows)
}
