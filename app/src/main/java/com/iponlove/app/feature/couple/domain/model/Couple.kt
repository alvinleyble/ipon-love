package com.iponlove.app.feature.couple.domain.model

/**
 * A pairing between two users. [user2Id] is null until a partner redeems the invite, so
 * [isAwaitingPartner] distinguishes "created, waiting to be joined" from "fully paired".
 *
 * [bannerUrl] is the optional premium couple-photo (v1.7.0 Item 10) — one shared banner for the
 * couple, written only via the `set_couple_banner` RPC (couples are RPC-write-only, ADR-0006/0008).
 * Null = no photo set → both surfaces fall back to Item 9's derived accent gradient.
 */
data class Couple(
    val id: String,
    val name: String,
    val inviteCode: String,
    val user1Id: String,
    val user2Id: String?,
    val isDeleted: Boolean,
    val bannerUrl: String? = null,
) {
    val isAwaitingPartner: Boolean get() = user2Id == null
}
