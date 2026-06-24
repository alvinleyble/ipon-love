package com.iponlove.app.feature.couple

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.couple.data.PairingErrorClassifier
import com.iponlove.app.feature.couple.domain.model.PairingError
import org.junit.Test

class PairingErrorClassifierTest {

    @Test
    fun mapsAlreadyInACouple() {
        assertThat(PairingErrorClassifier.classify("already in a couple"))
            .isEqualTo(PairingError.ALREADY_IN_COUPLE)
    }

    @Test
    fun mapsInvalidInviteCode() {
        assertThat(PairingErrorClassifier.classify("invalid invite code"))
            .isEqualTo(PairingError.INVALID_INVITE_CODE)
    }

    @Test
    fun mapsCoupleFull() {
        assertThat(PairingErrorClassifier.classify("couple is already full"))
            .isEqualTo(PairingError.COUPLE_FULL)
    }

    @Test
    fun mapsOwnCouple() {
        assertThat(PairingErrorClassifier.classify("cannot join your own couple"))
            .isEqualTo(PairingError.OWN_COUPLE)
    }

    @Test
    fun mapsNotInACouple() {
        assertThat(PairingErrorClassifier.classify("not in a couple"))
            .isEqualTo(PairingError.NOT_IN_COUPLE)
        assertThat(PairingErrorClassifier.classify("no couple to rotate"))
            .isEqualTo(PairingError.NOT_IN_COUPLE)
    }

    @Test
    fun mapsNetworkFailures() {
        assertThat(PairingErrorClassifier.classify("Failed to connect to host"))
            .isEqualTo(PairingError.NETWORK)
        assertThat(PairingErrorClassifier.classify("Connection timeout"))
            .isEqualTo(PairingError.NETWORK)
    }

    @Test
    fun unknownAndNullFallBackToUnknown() {
        assertThat(PairingErrorClassifier.classify("some unexpected error"))
            .isEqualTo(PairingError.UNKNOWN)
        assertThat(PairingErrorClassifier.classify(null)).isEqualTo(PairingError.UNKNOWN)
    }
}
