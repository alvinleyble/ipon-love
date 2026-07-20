package com.iponlove.app.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import com.iponlove.app.core.ui.theme.LeafShapes

/**
 * A Playful Pop editor dialog (v1.6.7 Item 8 Slice 6-PD): a drop-in wrapper over M3 [AlertDialog]
 * that swaps only the container *shape* for a gentle leaf-squircle ([LeafShapes.Dialog], ~18dp/8dp,
 * 0° tilt), bringing the app's five editor dialogs (Account, Category, Budget, Recurring rule,
 * PartnerDebt) onto the Playful card language without disturbing anything else.
 *
 * Per the 2026-07-21 grill (v1.6.7 Item 8 Slice-6 rollout, fork #2): the surface is leaf corners
 * only, *no* color fill — the M3 default opaque [AlertDialogDefaults.containerColor]
 * (`surfaceContainerHigh`) is already palette-derived and is exactly what the enclosed text fields
 * are designed against; blush/plum/glass all misbehave here (glass is near-transparent over the
 * scrim, blush washes near-white in dark mode, plum clashes with the fields). [containerColor] is
 * still exposed for the rare override.
 *
 * The slot API mirrors [AlertDialog] one-for-one so it's a literal drop-in at each call site.
 * [properties] is passed through so 6i's Recurring editor can keep its
 * `usePlatformDefaultWidth = false` near-full-width behavior.
 */
@Composable
fun PlayfulDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = AlertDialogDefaults.containerColor,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = LeafShapes.Dialog,
        containerColor = containerColor,
        properties = properties,
    )
}
