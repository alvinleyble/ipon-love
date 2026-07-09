package com.iponlove.app.core.config.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject

/** Postgrest-backed [AppConfigRemoteSource]. Reads the single public-read row. */
class SupabaseAppConfigRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : AppConfigRemoteSource {

    override suspend fun fetch(): AppConfigDto =
        client.from("app_config").select().decodeSingle()
}
