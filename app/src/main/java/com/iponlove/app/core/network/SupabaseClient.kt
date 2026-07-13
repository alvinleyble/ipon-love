package com.iponlove.app.core.network

import com.iponlove.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

/**
 * JSON used for Postgrest push serialization. `encodeDefaults = true` is the whole point:
 * supabase-kt's default serializer sets it *false*, so any DTO field left at its Kotlin
 * default (e.g. `isSettlement = false`) is silently omitted from that row's JSON. In a
 * batch upsert, Postgrest unions the keys present across all rows into one column list, then
 * writes literal SQL NULL for any row missing a unioned key — bypassing the column's
 * `default` and violating our `not null` constraints (the classic mixed-batch failure, e.g.
 * `transactions.is_settlement`). Encoding defaults makes every row carry every column, so a
 * batch mixing true/false rows pushes cleanly. Safe because all pushes are full-row
 * `.upsert()` (LWW, ADR-0001/0002) — no partial `.update()` relies on field omission.
 * `ignoreUnknownKeys` mirrors supabase-kt's default so pulls tolerate new server columns.
 */
internal val iponPostgrestJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

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
    install(Postgrest) {
        serializer = KotlinXSerializer(iponPostgrestJson)
    }
    install(Storage)
    // Realtime powers the couple "bell" (ADR-0015): a content-less Broadcast ping that
    // tells the partner to pull. The websocket connects lazily, only when a channel
    // subscribes (CoupleChannelManager, foreground + paired only).
    install(Realtime)
}
