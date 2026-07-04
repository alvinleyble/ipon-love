package com.iponlove.app.feature.recurring.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.recurring.data.local.RecurringRuleDao
import com.iponlove.app.feature.recurring.data.local.RecurringRuleEntity
import com.iponlove.app.feature.recurring.domain.model.RecurringRule
import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [RecurringRuleRepository]. The single place every rule write applies the
 * sync bookkeeping: a fresh monotonic `updated_at` (ADR-0001) and `pending_sync`
 * (ADR-0002); deletes are soft (ADR-0010). Ownership survives edits — including the
 * `nextDate` cursor advance the materialization pass writes.
 */
class RecurringRuleRepositoryImpl @Inject constructor(
    private val dao: RecurringRuleDao,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : RecurringRuleRepository {

    override fun observeRules(): Flow<List<RecurringRule>> =
        dao.observeRules().map { rows -> rows.map { it.toDomain() } }

    override suspend fun activeRules(): List<RecurringRule> =
        dao.activeRules().map { it.toDomain() }

    override suspend fun getRule(id: String): RecurringRule? = dao.getById(id)?.toDomain()

    override suspend fun upsertRule(rule: RecurringRule) {
        val existing = dao.getById(rule.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        dao.upsert(
            RecurringRuleEntity(
                id = rule.id,
                userId = existing?.userId ?: currentUser.userId(),
                frequency = rule.frequency,
                interval = rule.interval,
                nextDate = rule.nextDate,
                endDate = rule.endDate,
                templateAmount = rule.template.amount,
                templateAccountId = rule.template.accountId,
                templateCategoryId = rule.template.categoryId,
                templateNote = rule.template.note,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
                isPaused = rule.isPaused,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun deleteRule(id: String) {
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
}
