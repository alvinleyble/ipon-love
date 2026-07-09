package com.iponlove.app.core.entitlement

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CapCountTest {

    @Test
    fun belowLimit_doesNotBlock() {
        assertThat(CapCount.blocksCreate(currentCount = 9, limit = 10)).isFalse()
    }

    @Test
    fun atLimit_blocks() {
        assertThat(CapCount.blocksCreate(currentCount = 10, limit = 10)).isTrue()
    }

    @Test
    fun overLimit_stillBlocks() {
        // Tolerated transient overshoot (concurrent cross-device creates, G3) never un-blocks.
        assertThat(CapCount.blocksCreate(currentCount = 11, limit = 10)).isTrue()
    }

    @Test
    fun zeroLimit_blocksImmediately() {
        // maxNoteAttachments = 0 on the free tier.
        assertThat(CapCount.blocksCreate(currentCount = 0, limit = 0)).isTrue()
    }
}
