package com.iponlove.app.core.config

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.config.local.AppConfigDao
import com.iponlove.app.core.config.local.AppConfigEntity
import com.iponlove.app.core.config.remote.AppConfigDto
import com.iponlove.app.core.config.remote.AppConfigRemoteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.time.Instant

class AppConfigRepositoryImplTest {

    private val ts = Instant.ofEpochMilli(1_000)

    private class FakeDao : AppConfigDao {
        val flow = MutableStateFlow<AppConfigEntity?>(null)
        override suspend fun upsert(config: AppConfigEntity) { flow.value = config }
        override fun observe(): Flow<AppConfigEntity?> = flow
    }

    private class FakeRemote(var result: Result<AppConfigDto>) : AppConfigRemoteSource {
        override suspend fun fetch(): AppConfigDto = result.getOrThrow()
    }

    private fun repo(dao: AppConfigDao, remote: AppConfigRemoteSource) =
        AppConfigRepositoryImpl(dao, remote)

    @Test
    fun absentRow_emitsDormantFallback() = runTest {
        val r = repo(FakeDao(), FakeRemote(Result.failure(IllegalStateException("unused"))))

        r.observe().test {
            assertThat(awaitItem()).isEqualTo(AppConfig.DORMANT)
        }
    }

    @Test
    fun refresh_cachesRow_thenObserveEmitsIt() = runTest {
        val dao = FakeDao()
        val remote = FakeRemote(
            Result.success(
                AppConfigDto(
                    enforcementEnabled = true,
                    capOverrides = JsonObject(mapOf("maxSavingsGoals" to JsonPrimitive(3))),
                    updatedAt = ts,
                ),
            ),
        )

        repo(dao, remote).refresh()

        repo(dao, remote).observe().test {
            val config = awaitItem()
            assertThat(config.enforcementEnabled).isTrue()
            assertThat(config.capOverridesJson).contains("maxSavingsGoals")
        }
    }

    @Test
    fun refresh_networkFailure_leavesCacheIntact() = runTest {
        val dao = FakeDao()
        val remote = FakeRemote(Result.failure(RuntimeException("offline")))

        // Must not throw, and cache stays empty → dormant.
        repo(dao, remote).refresh()

        repo(dao, remote).observe().test {
            assertThat(awaitItem()).isEqualTo(AppConfig.DORMANT)
        }
    }

    @Test
    fun refresh_withNullCapOverrides_storesNull() = runTest {
        val dao = FakeDao()
        val remote = FakeRemote(
            Result.success(AppConfigDto(enforcementEnabled = false, capOverrides = null, updatedAt = ts)),
        )

        repo(dao, remote).refresh()

        repo(dao, remote).observe().test {
            assertThat(awaitItem().capOverridesJson).isNull()
        }
    }
}
