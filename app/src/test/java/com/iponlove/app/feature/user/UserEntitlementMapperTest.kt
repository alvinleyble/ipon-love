package com.iponlove.app.feature.user

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserDto
import com.iponlove.app.feature.user.data.remote.UserPushDto
import com.iponlove.app.feature.user.data.toEntitlementWrite
import com.iponlove.app.feature.user.data.toEntity
import com.iponlove.app.feature.user.data.toPushDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.time.Instant

/**
 * Entitlement must still arrive from the server on pull (D2 / ADR-0044 — a purchase/grant has
 * to reach the partner), while the ordinary push must NOT carry it (ADR-0060 — the four columns
 * are write-locked, and the privilege check rejects a statement that merely names them).
 */
class UserEntitlementMapperTest {

    private val ts = Instant.ofEpochMilli(1_000)
    private val premiumTs = Instant.ofEpochMilli(9_999)

    private fun entity(
        isPremium: Boolean = false,
        premiumUntil: Instant? = null,
        entitlementSource: String = "NONE",
        entitlementCheckedAt: Instant? = null,
    ) = UserEntity(
        id = "u1",
        displayName = "Alvin",
        avatarUrl = null,
        accentColor = null,
        coupleId = null,
        isPremium = isPremium,
        premiumUntil = premiumUntil,
        entitlementSource = entitlementSource,
        entitlementCheckedAt = entitlementCheckedAt,
        createdAt = ts,
        updatedAt = ts,
        isDeleted = false,
        serverRev = 5,
        pendingSync = false,
    )

    @Test
    fun defaults_areDormant() {
        val e = entity()
        assertThat(e.isPremium).isFalse()
        assertThat(e.premiumUntil).isNull()
        assertThat(e.entitlementSource).isEqualTo("NONE")
        assertThat(e.entitlementCheckedAt).isNull()
    }

    /**
     * The load-bearing assertion of ADR-0060. Postgres rejects an UPDATE that *names* a locked
     * column even when the value is unchanged, so this is asserted on the serialized wire shape
     * — the thing the database actually sees — not just on the Kotlin type.
     */
    @Test
    fun pushPayload_omitsAllFourEntitlementColumns() {
        val dto = entity(
            isPremium = true,
            premiumUntil = premiumTs,
            entitlementSource = "PLAY",
            entitlementCheckedAt = ts,
        ).toPushDto()

        val keys = Json.encodeToJsonElement<UserPushDto>(dto).jsonObject.keys

        assertThat(keys).containsNoneOf(
            "is_premium", "premium_until", "entitlement_source", "entitlement_checked_at",
        )
    }

    /**
     * The other half: the ordinary profile columns must still ride the push, or a profile edit
     * would silently stop syncing. Every one of these is in the database's UPDATE allowlist —
     * this test and that grant have to be kept in step.
     *
     * A motif is set explicitly because `avatarMotif` is the one field with a default, and
     * kotlinx omits defaults; that is pre-existing wire behaviour, unchanged by ADR-0060.
     */
    @Test
    fun pushPayload_stillCarriesProfileColumns() {
        val dto = entity().copy(avatarMotif = "leaf").toPushDto()

        val keys = Json.encodeToJsonElement<UserPushDto>(dto).jsonObject.keys

        assertThat(keys).containsExactly(
            "id", "display_name", "avatar_url", "accent_color", "avatar_motif",
            "couple_id", "created_at", "updated_at", "server_rev",
        )
    }

    /** Entitlement still leaves the device — via the RPC payload instead of the upsert. */
    @Test
    fun entitlementWrite_carriesTheGrant() {
        val write = entity(
            isPremium = true,
            premiumUntil = null,
            entitlementSource = "GRANT",
            entitlementCheckedAt = ts,
        ).toEntitlementWrite()

        assertThat(write.isPremium).isTrue()
        assertThat(write.premiumUntil).isNull()
        assertThat(write.source).isEqualTo("GRANT")
        assertThat(write.checkedAt).isEqualTo(ts)
        // The row's LWW key travels with it so the server write keeps ADR-0001's ordering.
        assertThat(write.updatedAt).isEqualTo(ts)
    }

    /** Reads are untouched by ADR-0060 — pull still brings the partner's entitlement down. */
    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse() {
        val dto = UserDto(
            id = "partner",
            displayName = "Patty",
            avatarUrl = null,
            accentColor = null,
            coupleId = "c1",
            isPremium = true,
            premiumUntil = null,
            entitlementSource = "PLAY",
            entitlementCheckedAt = ts,
            createdAt = ts,
            updatedAt = ts,
            serverRev = 7,
        )

        val entity = dto.toEntity()

        assertThat(entity.isPremium).isTrue()
        assertThat(entity.entitlementSource).isEqualTo("PLAY")
        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
    }
}
