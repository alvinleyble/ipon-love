package com.iponlove.app.feature.export

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.export.domain.AttachmentLimits
import org.junit.Test

/**
 * The 100-photo ceiling on an attachment export (v1.7.0 Item 6 decision 4). Boundary-exact on
 * purpose: the cap is refused *before* the tap, so an off-by-one here either blocks a legitimate
 * export or starts a download the grill decided was too long to ask a PH mobile connection for.
 */
class AttachmentLimitsTest {

    @Test
    fun `the cap is one hundred photos`() {
        assertThat(AttachmentLimits.MAX_PHOTOS).isEqualTo(100)
    }

    @Test
    fun `exactly at the cap is allowed`() {
        assertThat(AttachmentLimits.exceeded(100)).isFalse()
    }

    @Test
    fun `one over the cap is refused`() {
        assertThat(AttachmentLimits.exceeded(101)).isTrue()
    }

    @Test
    fun `an export with no photos is never capped`() {
        assertThat(AttachmentLimits.exceeded(0)).isFalse()
    }
}
