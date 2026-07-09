package com.iponlove.app.core.config

import com.iponlove.app.core.config.local.AppConfigDao
import com.iponlove.app.core.config.local.AppConfigEntity
import com.iponlove.app.core.config.remote.AppConfigRemoteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AppConfigRepositoryImpl @Inject constructor(
    private val dao: AppConfigDao,
    private val remote: AppConfigRemoteSource,
) : AppConfigRepository {

    override fun observe(): Flow<AppConfig> =
        dao.observe().map { entity ->
            entity?.let {
                AppConfig(
                    enforcementEnabled = it.enforcementEnabled,
                    capOverridesJson = it.capOverridesJson,
                )
            } ?: AppConfig.DORMANT   // fail-open cold-start (G4 / ADR-0044 §6)
        }

    override suspend fun refresh() {
        // Best-effort: offline / transient failure leaves the last-known-good cache in place
        // (or the dormant fallback on a fresh install). Never surfaces to the UI.
        runCatching {
            val dto = remote.fetch()
            dao.upsert(
                AppConfigEntity(
                    id = true,
                    enforcementEnabled = dto.enforcementEnabled,
                    capOverridesJson = dto.capOverrides?.toString(),
                    updatedAt = dto.updatedAt,
                ),
            )
        }
    }
}
