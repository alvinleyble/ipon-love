package com.iponlove.app.feature.appupdate.data

import com.iponlove.app.feature.appupdate.data.remote.AppReleaseInfoDto
import com.iponlove.app.feature.appupdate.domain.repository.AppReleaseInfoRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject

class AppReleaseInfoRepositoryImpl @Inject constructor(
    private val client: SupabaseClient,
) : AppReleaseInfoRepository {

    override suspend fun getRequiredVersionCode(): Int =
        client.from("app_release_info").select().decodeSingle<AppReleaseInfoDto>().requiredVersionCode
}
