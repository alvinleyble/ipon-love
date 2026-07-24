package com.iponlove.app.feature.export.domain.model

/**
 * The three export shapes (v1.7.0 Item 6). [CSV] is Slice 1 — free, text-only, no network. [PDF]
 * and [ZIP] are Slice 2 — they bundle receipt photos, so they cost Storage egress and sit behind
 * the `EXPORT_WITH_ATTACHMENTS` soft gate (decision 2), need connectivity for any photo not still
 * on local disk (decision 3a), and honour the [AttachmentLimits.MAX_PHOTOS] cap (decision 4).
 */
enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val label: String,
) {
    CSV("csv", "text/csv", "CSV"),
    PDF("pdf", "application/pdf", "PDF"),
    ZIP("zip", "application/zip", "ZIP"),
    ;

    /** True for the formats that carry receipt photos — the gated, network-touching pair. */
    val carriesAttachments: Boolean get() = this != CSV
}
