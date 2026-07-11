package com.iponlove.app.core.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The push ownership predicate that keeps a single un-pushable row from wedging a flip-model
 * table's atomic upsert batch (v1.6.5 Item 20).
 */
class PushOwnershipTest {

    private val me = "user-1"
    private val myCouple = "couple-1"

    @Test
    fun ownPersonalRow_isPushable() {
        assertThat(isLocallyPushable(userId = me, coupleId = null, me = me, myCoupleId = myCouple)).isTrue()
    }

    @Test
    fun currentCoupleRow_isPushable() {
        assertThat(isLocallyPushable(userId = null, coupleId = myCouple, me = me, myCoupleId = myCouple)).isTrue()
    }

    @Test
    fun partnerOwnedRow_isNotPushable() {
        // The un-share poison row: reverted onto the partner's user_id (Item 20).
        assertThat(isLocallyPushable(userId = "user-2", coupleId = null, me = me, myCoupleId = myCouple)).isFalse()
    }

    @Test
    fun staleCoupleRow_fromDissolvedCouple_isNotPushable() {
        assertThat(isLocallyPushable(userId = null, coupleId = "old-couple", me = me, myCoupleId = myCouple)).isFalse()
    }

    @Test
    fun whenUnpaired_ownPersonalStillPushable_butAnyCoupleRowIsStale() {
        assertThat(isLocallyPushable(userId = me, coupleId = null, me = me, myCoupleId = null)).isTrue()
        assertThat(isLocallyPushable(userId = null, coupleId = "couple-1", me = me, myCoupleId = null)).isFalse()
    }
}
