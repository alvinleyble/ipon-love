package com.iponlove.app.feature.settings.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject

/**
 * Postgrest-backed [AccountDeletionRemoteSource] — invokes the `delete_account()` SECURITY
 * DEFINER RPC, mirroring the pairing RPCs in `SupabaseCoupleRemoteSource`. The RPC returns void;
 * a thrown exception (network / RLS / not-authenticated) propagates so the repository aborts the
 * teardown with nothing wiped.
 */
class SupabaseAccountDeletionRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : AccountDeletionRemoteSource {
    override suspend fun deleteAccount() {
        client.postgrest.rpc("delete_account")
    }
}
