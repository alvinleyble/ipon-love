package com.iponlove.app.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ListReorderTest {

    @Test
    fun movedTo_reordersAndShiftsTheRest() {
        val list = listOf("a", "b", "c", "d")
        assertThat(list.movedTo(0, 3)).containsExactly("b", "c", "d", "a").inOrder()
        assertThat(list.movedTo(3, 0)).containsExactly("d", "a", "b", "c").inOrder()
    }

    @Test
    fun movedTo_isNoOpWhenIndicesEqualOrFromOutOfRange() {
        val list = listOf("a", "b", "c")
        assertThat(list.movedTo(1, 1)).isEqualTo(list)
        assertThat(list.movedTo(5, 0)).isEqualTo(list)
        assertThat(list.movedTo(-1, 0)).isEqualTo(list)
    }

    @Test
    fun movedTo_clampsOutOfRangeTo() {
        val list = listOf("a", "b", "c")
        assertThat(list.movedTo(0, 99)).containsExactly("b", "c", "a").inOrder()
    }
}
