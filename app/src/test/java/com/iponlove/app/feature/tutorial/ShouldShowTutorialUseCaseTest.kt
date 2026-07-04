package com.iponlove.app.feature.tutorial

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.tutorial.domain.repository.TutorialRepository
import com.iponlove.app.feature.tutorial.domain.usecase.ShouldShowTutorialUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The first-run tutorial gate (ADR-0034): purely the local seen-flag, no sync/emptiness check. */
class ShouldShowTutorialUseCaseTest {

    private var seen = false

    private val repository = object : TutorialRepository {
        override suspend fun isTutorialSeen(): Boolean = seen
        override suspend fun setTutorialSeen() { seen = true }
    }

    private val useCase = ShouldShowTutorialUseCase(repository)

    @Test
    fun notSeen_showsTutorial() = runTest {
        assertThat(useCase()).isTrue()
    }

    @Test
    fun alreadySeen_doesNotShow() = runTest {
        seen = true

        assertThat(useCase()).isFalse()
    }
}
