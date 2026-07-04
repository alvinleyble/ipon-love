package com.iponlove.app.core.network

import com.iponlove.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/** Builds the app's single [SupabaseClient] from the build-config credentials. */
fun createIponSupabaseClient(): SupabaseClient = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
) {
    install(Auth) {
        // handleDeeplinks() (MainActivity) silently no-ops unless these match the incoming
        // URI's scheme/host exactly — they're null by default in the SDK, not inferred from
        // the manifest's intent-filter. Confirmed via bytecode: without this, every deep link
        // (email confirmation, password recovery) was being dropped before it even looked at
        // the fragment. Must match AndroidManifest.xml's login-callback intent-filter.
        scheme = "com.iponlove.app"
        host = "login-callback"
    }
    install(Postgrest)
    install(Storage)
    // Realtime powers the couple "bell" (ADR-0015): a content-less Broadcast ping that
    // tells the partner to pull. The websocket connects lazily, only when a channel
    // subscribes (CoupleChannelManager, foreground + paired only).
    install(Realtime)
}
