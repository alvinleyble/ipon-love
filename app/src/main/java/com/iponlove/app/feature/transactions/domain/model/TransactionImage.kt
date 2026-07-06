package com.iponlove.app.feature.transactions.domain.model

/**
 * A receipt photo attached to a transaction (up to 3, capped client-side). Either [localPath]
 * (a compressed file pending upload) or [url] (the uploaded Supabase Storage URL) is set; the
 * uploader clears [localPath] and stamps [url] once the file reaches Storage.
 */
data class TransactionImage(
    val id: String,
    val transactionId: String,
    val localPath: String? = null,
    val url: String? = null,
    val position: Int = 0,
) {
    companion object {
        /** Max receipt photos per transaction (mirrors notes' cap). Enforced in the editor
         *  (UI + ViewModel), the save use case, and the repository as a hard backstop. */
        const val MAX = 3
    }
}
