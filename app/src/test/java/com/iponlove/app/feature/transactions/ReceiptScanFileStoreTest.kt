package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.data.ReceiptScanFileStore
import com.iponlove.app.feature.transactions.data.ReceiptScanFileStore.Companion.MAX_AGE_MS
import com.iponlove.app.feature.transactions.data.ReceiptScanFileStore.Companion.isExpired
import org.junit.Test

/**
 * The age predicate behind the `cacheDir/scans` startup sweep (ADR-0062 decision 9 — the second
 * review's highest-value finding).
 *
 * The sweep is deliberately **not** a copy of `ExportFileWriter.sweep()`, which deletes everything
 * in its directory. `ACTION_IMAGE_CAPTURE` hands off to a separate camera process, and on a
 * low-RAM device — this app's PH budget-Android market — the app's process is routinely killed
 * while the camera is foreground. `ActivityResultRegistry` redelivers the pending result across
 * that restart, so an unconditional sweep at `IponApp.onCreate` would fire on exactly that restart
 * and delete the in-flight capture before the redelivered result could be read, surfacing as an
 * intermittent "Couldn't read that one" on precisely the devices most likely to hit it.
 */
class ReceiptScanFileStoreTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `a capture taken seconds ago survives the sweep`() {
        // The process-death case the age threshold exists for: the camera is still foreground and
        // the result is about to be redelivered.
        assertThat(isExpired(lastModifiedMs = now - 5_000, nowMs = now)).isFalse()
    }

    @Test
    fun `a capture from several minutes ago still survives`() {
        assertThat(isExpired(lastModifiedMs = now - 10 * 60_000, nowMs = now)).isFalse()
    }

    @Test
    fun `a capture abandoned for over an hour is swept`() {
        assertThat(isExpired(lastModifiedMs = now - MAX_AGE_MS - 1, nowMs = now)).isTrue()
    }

    @Test
    fun `a capture exactly at the threshold is kept`() {
        // Strictly older-than, so the boundary never races a capture that is still in flight.
        assertThat(isExpired(lastModifiedMs = now - MAX_AGE_MS, nowMs = now)).isFalse()
    }

    @Test
    fun `a capture from a previous day is swept`() {
        assertThat(isExpired(lastModifiedMs = now - 24 * 60 * 60_000L, nowMs = now)).isTrue()
    }

    @Test
    fun `a clock that moved backwards never sweeps an existing capture`() {
        // Defensive: a device clock correction must not make a live capture look ancient.
        assertThat(isExpired(lastModifiedMs = now + 60_000, nowMs = now)).isFalse()
    }

    @Test
    fun `the threshold is one hour`() {
        assertThat(MAX_AGE_MS).isEqualTo(60 * 60 * 1000L)
    }
}
