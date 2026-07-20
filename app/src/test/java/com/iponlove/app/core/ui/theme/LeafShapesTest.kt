package com.iponlove.app.core.ui.theme

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LeafShapesTest {

    @Test
    fun evenIndexLeansDefault() {
        assertThat(LeafShapes.leafFor(0, 22.dp, 9.dp)).isEqualTo(LeafShapes.leaf(22.dp, 9.dp))
    }

    @Test
    fun oddIndexLeansMirrored() {
        assertThat(LeafShapes.leafFor(1, 22.dp, 9.dp)).isEqualTo(LeafShapes.leafMirrored(22.dp, 9.dp))
    }

    @Test
    fun alternatesBackOnNextEven() {
        assertThat(LeafShapes.leafFor(2, 22.dp, 9.dp)).isEqualTo(LeafShapes.leaf(22.dp, 9.dp))
    }

    @Test
    fun adjacentIndicesDiffer() {
        assertThat(LeafShapes.leafFor(0, 22.dp, 9.dp))
            .isNotEqualTo(LeafShapes.leafFor(1, 22.dp, 9.dp))
    }
}
