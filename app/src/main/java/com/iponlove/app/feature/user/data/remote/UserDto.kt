package com.iponlove.app.feature.user.data.remote

import com.iponlove.app.core.network.serializers.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Wire shape of a `users` row as Supabase *returns* it (pull + fetchSelf). No `isDeleted` —
 * users are never soft-deleted (the column doesn't exist in the schema). No `pendingSync` —
 * local-only (ADR-0002).
 *
 * Reads are entirely untouched by ADR-0060: own and partner entitlement still arrive through
 * the ordinary pull. Only the *push* shape is constrained — see [UserPushDto].
 */
@Serializable
data class UserDto(
    val id: String,
    @SerialName("display_name") val displayName: String?,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("accent_color") val accentColor: String?,
    // Free motif-avatar key (v1.6.7 Item 3 Leg 1) — round-trips through sync so the partner sees it;
    // not redacted (partner-visible by design, same as accent_color). Default null = Heart.
    @SerialName("avatar_motif") val avatarMotif: String? = null,
    @SerialName("couple_id") val coupleId: String?,
    // Premium entitlement (dormant paywall infra, D2 / ADR-0044). Round-trips through sync
    // so a purchase/grant reaches the partner; not redacted (partner-visible by design).
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("premium_until")
    @Serializable(with = InstantSerializer::class) val premiumUntil: Instant? = null,
    @SerialName("entitlement_source") val entitlementSource: String = "NONE",
    @SerialName("entitlement_checked_at")
    @Serializable(with = InstantSerializer::class) val entitlementCheckedAt: Instant? = null,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("server_rev") val serverRev: Long?,
)

/**
 * Push shape of a `users` row: the ordinary full-row upsert **minus the four entitlement
 * columns**, which `authenticated` may no longer write (ADR-0060).
 *
 * This split is load-bearing, not tidiness. Postgres' column privilege check keys on a column
 * being *named* in the statement, not on its value changing — and the users push is a full-row
 * upsert, so a row dirtied by an accent-colour change alone still carried `is_premium` &co. in
 * the payload. Shipping them here would make **every ordinary profile edit** fail, not just
 * spoof attempts. Entitlement travels through `set_self_entitlement()` instead
 * ([UserEntitlementWrite]).
 */
@Serializable
data class UserPushDto(
    val id: String,
    @SerialName("display_name") val displayName: String?,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("accent_color") val accentColor: String?,
    @SerialName("avatar_motif") val avatarMotif: String? = null,
    @SerialName("couple_id") val coupleId: String?,
    @SerialName("created_at")
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @SerialName("server_rev") val serverRev: Long?,
)

/**
 * Arguments for the `set_self_entitlement` RPC — the sole write path for the four locked
 * columns (ADR-0060). Plain Kotlin rather than a `@Serializable` DTO because the house RPC
 * style builds parameters with `buildJsonObject` at the call site; keeping this pure makes the
 * mapping unit-testable without a serializer.
 */
data class UserEntitlementWrite(
    val isPremium: Boolean,
    val premiumUntil: Instant?,
    val source: String,
    val checkedAt: Instant?,
    val updatedAt: Instant,
)
