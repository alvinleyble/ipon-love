package com.iponlove.app.feature.couple.domain.model

/**
 * A pairing between two users. [user2Id] is null until a partner redeems the invite, so
 * [isAwaitingPartner] distinguishes "created, waiting to be joined" from "fully paired".
 */
data class Couple(
    val id: String,
    val name: String,
    val inviteCode: String,
    val user1Id: String,
    val user2Id: String?,
    val isDeleted: Boolean,
) {
    val isAwaitingPartner: Boolean get() = user2Id == null
}
