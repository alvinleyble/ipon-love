package com.iponlove.app.feature.export.domain.model

/**
 * One receipt photo referenced by an export (v1.7.0 Item 6 Slice 2). Mirrors the two states a
 * `transaction_images` row can be in: still on local disk pending upload ([localPath]), or already
 * in the private `receipts` Storage bucket ([url]) — the uploader clears the former when it stamps
 * the latter, so in practice exactly one is set.
 *
 * The fetcher prefers the local file and falls back to a download (decision 3), which is why both
 * are carried rather than collapsing to a single source at mapping time.
 */
data class ExportPhoto(
    val id: String,
    val localPath: String? = null,
    val url: String? = null,
) {
    /**
     * Whether exporting this photo would have to hit the network — the input to the offline block
     * (decision 3a) and to "is this export downloadable at all". Best-effort by design: a
     * [localPath] whose file has since vanished still degrades to a download at fetch time, and to
     * "Receipt unavailable" if that also fails.
     */
    val needsDownload: Boolean get() = localPath == null
}
