package com.iponlove.app.feature.transactions.presentation.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.transactions.presentation.ReceiptScanFailure
import com.iponlove.app.feature.transactions.presentation.ReceiptScanUiState
import java.io.File

/**
 * The receipt-scan affordance, shared by the full New transaction form and the widget's Quick Add
 * sheet (v1.7.3 Item 14, [ADR-0067][docs/adr/0067-quick-add-scan-and-drafts-via-shared-usecases.md]).
 *
 * Extracted here rather than duplicated into `feature/widget` on purpose: the pieces below encode
 * ADR-0062's gating rules (both doors carry the same `Feature.RECEIPT_SCANNING` gate, the lock
 * glyph only ever appears under enforcement, a failed read stays in the camera). Two copies would
 * be two places for those rules to drift, and the widget sheet is the surface least likely to be
 * looked at when one of them changes.
 */

/**
 * The two scan doors, side by side (ADR-0062 decision 3, changed 2026-08-04 from a single CTA plus
 * a chooser sheet — one less tap, and both routes are named).
 *
 * Both carry the same `Feature.RECEIPT_SCANNING` gate: the gallery leg is the same feature from a
 * different source, so gating only the camera would leave a free bypass. That leg is deliberately
 * full-width and labelled rather than demoted to an icon, because it carries the GCash/Maya/GrabPay
 * screenshot case — the cleanest, most reliable read this app's receipts get.
 */
@Composable
internal fun ScanEntryRow(
    scan: ReceiptScanUiState,
    onScanTap: () -> Unit,
    onScanFromGalleryTap: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScanEntryButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.PhotoCamera,
            label = "Scan receipt",
            locked = scan.locked,
            enabled = !scan.inProgress,
            onClick = onScanTap,
        )
        ScanEntryButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.PhotoLibrary,
            label = "From gallery",
            locked = scan.locked,
            enabled = !scan.inProgress,
            onClick = onScanFromGalleryTap,
        )
    }
    if (scan.inProgress) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Reading receipt…",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ScanEntryButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    locked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            // The lock glyph only ever appears under enforcement; the gate ships dormant.
            imageVector = if (locked) Icons.Filled.Lock else icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
    }
}

/**
 * The scanned receipt, shown large above the fields during review. [height] is shorter in the Quick
 * Add sheet, which has a whole form to fit above the fold.
 *
 * [onView] is null where there is no full-screen viewer to open — the Quick Add sheet hosts no
 * image pager — so the preview isn't made to look tappable there.
 */
@Composable
internal fun ScanPreview(path: String, onView: (() -> Unit)? = null, height: Dp = 160.dp) {
    AsyncImage(
        model = File(path),
        contentDescription = "Scanned receipt",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onView != null) Modifier.clickable(onClick = onView) else Modifier),
    )
}

@Composable
internal fun ScanHint(text: String, tone: Color = LocalPlayfulColors.current.accent) {
    val colors = LocalPlayfulColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(10.dp),
    )
}

/**
 * The caption under an *inferred* field (ADR-0062 decision 4). Only Category and Account carry one:
 * the three read fields are verifiable against the photo shown at the top of the form, while these
 * two appear nowhere on the receipt — without a caption, a user comparing form to photo has no way
 * to account for them.
 */
@Composable
internal fun InferredCaption(merchant: String) {
    Text(
        "From your last $merchant visit",
        style = MaterialTheme.typography.bodySmall,
        color = LocalPlayfulColors.current.textSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * A failed read stays in the camera (ADR-0062 decision 8): retaking is both the actual fix for a
 * bad frame and one tap away. Falling through to a blank form was rejected — it makes typing the
 * path of least resistance exactly when a second photo would have worked.
 */
@Composable
internal fun ScanFailedDialog(
    failure: ReceiptScanFailure,
    onRetake: () -> Unit,
    onEnterManually: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onEnterManually,
        title = { Text("Couldn't read that one") },
        text = {
            Text(
                when (failure) {
                    ReceiptScanFailure.NO_TEXT ->
                        "Try again with the whole receipt in frame, in brighter light."
                    ReceiptScanFailure.NOTHING_USABLE ->
                        "We found some text but nothing we could use. Make sure the total is in " +
                            "frame and the receipt is flat."
                },
            )
        },
        confirmButton = { TextButton(onClick = onRetake) { Text("Retake") } },
        dismissButton = { TextButton(onClick = onEnterManually) { Text("Enter manually") } },
    )
}

/** True only on API 26-28 with the legacy storage permission still ungranted. */
internal fun needsLegacyGalleryPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) != PackageManager.PERMISSION_GRANTED
