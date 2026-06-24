package com.iponlove.app.feature.categories

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.categories.data.toDomain
import com.iponlove.app.feature.categories.data.toDto
import com.iponlove.app.feature.categories.data.toEntity
import com.iponlove.app.feature.categories.domain.model.CategoryType
import org.junit.Test
import java.time.Instant

class CategoryMapperTest {

    @Test
    fun entityToDomain_keepsTypeAndDropsSyncColumns() {
        val domain = categoryEntity(id = "c", name = "Salary", type = CategoryType.INCOME).toDomain()

        assertThat(domain.id).isEqualTo("c")
        assertThat(domain.name).isEqualTo("Salary")
        assertThat(domain.type).isEqualTo(CategoryType.INCOME)
    }

    @Test
    fun entityToDto_carriesServerRev_andOmitsPendingSyncByConstruction() {
        val dto = categoryEntity(id = "c", serverRev = 42, pendingSync = true).toDto()

        assertThat(dto.id).isEqualTo("c")
        assertThat(dto.serverRev).isEqualTo(42)
        // CategoryDto has no pendingSync field at all — it can never go over the wire.
    }

    @Test
    fun dtoToEntity_isServerCanonical_pendingSyncFalse_andKeepsServerRev() {
        val entity = categoryDto(id = "c", serverRev = 7, updatedAt = Instant.ofEpochMilli(5_000))
            .toEntity()

        assertThat(entity.pendingSync).isFalse()
        assertThat(entity.serverRev).isEqualTo(7)
        assertThat(entity.updatedAt).isEqualTo(Instant.ofEpochMilli(5_000))
    }

    @Test
    fun entityToDto_roundTrips() {
        val original = categoryEntity(id = "c", name = "Rent", serverRev = 9)

        val roundTripped = original.toDto().toEntity()

        assertThat(roundTripped).isEqualTo(original.copy(pendingSync = false))
    }
}
