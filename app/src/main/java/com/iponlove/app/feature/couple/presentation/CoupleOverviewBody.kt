package com.iponlove.app.feature.couple.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.AccentColorRow
import com.iponlove.app.core.ui.BannerSource
import com.iponlove.app.core.ui.CoupleBanner
import com.iponlove.app.core.ui.CoupleBannerCropDialog
import com.iponlove.app.core.ui.CouplePhotoOverlap
import com.iponlove.app.core.ui.FullScreenImagePager
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.effectiveBannerSource
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.couple.domain.model.PairingError
import com.iponlove.app.feature.couple.domain.model.PairingState

private const val INVITE_LANDING_URL = "https://loveipon.app/invite"

/**
 * Pairing/unpair body — create-or-join when unpaired, invite-code sharing + unpair when paired.
 * Chrome-less (no Scaffold): reused by both Settings → Couple (relocated destination, ADR-0024)
 * and the onboarding pair-or-solo step's "Invite"/"Code" sub-forms.
 *
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6h): Cards → leaf-squircle [PlayfulCard]s,
 * every Text explicitly Playful-tinted (the host Scaffold is transparent, so an implicit content
 * color would fall back to black — the 6a black-fallback rule). Form controls (text fields,
 * buttons, `AccentColorRow`) and the one-off unpair confirm dialog stay conservative M3.
 */
@Composable
fun CoupleOverviewBody(
    state: CoupleUiState,
    viewModel: CoupleViewModel,
    modifier: Modifier = Modifier,
    onOpenPremium: (source: String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.error?.let { ErrorBanner(it) }

        when (val pairing = state.pairing) {
            PairingState.Loading ->
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

            PairingState.NotPaired -> NotPairedContent(state, viewModel)

            is PairingState.Paired ->
                PairedContent(pairing, state, viewModel, state.currentDisplayName, onOpenPremium)
        }
    }
}

@Composable
internal fun NotPairedContent(state: CoupleUiState, viewModel: CoupleViewModel) {
    val colors = LocalPlayfulColors.current

    // Single color picker shown once — shared by both create and join flows.
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your accent color", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            AccentColorRow(
                selectedHex = state.selectedColor,
                enabled = !state.isWorking,
                onSelect = viewModel::onColorSelected,
            )
        }
    }

    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafMirrored(22.dp, 9.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create a couple", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                "Start a shared space and send your partner the invite code.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            OutlinedTextField(
                value = state.nameInput,
                onValueChange = viewModel::onNameChange,
                label = { Text("Couple name") },
                singleLine = true,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::createCouple,
                enabled = state.canCreate,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create couple") }
        }
    }

    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Join a couple", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                "Already have a code from your partner? Enter it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            OutlinedTextField(
                value = state.codeInput,
                onValueChange = viewModel::onCodeChange,
                label = { Text("Invite code") },
                singleLine = true,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::redeemInvite,
                enabled = state.canRedeem,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Join couple") }
        }
    }
}

@Composable
internal fun PairedContent(
    paired: PairingState.Paired,
    state: CoupleUiState,
    viewModel: CoupleViewModel,
    currentDisplayName: String?,
    onOpenPremium: (source: String) -> Unit = {},
) {
    val colors = LocalPlayfulColors.current
    var confirmUnpair by remember { mutableStateOf(false) }
    val couple = paired.couple

    // Item 10: pick a photo → hand-rolled square crop → upload. The photo shows only while
    // unlocked (dormant ⇒ always); tapping while locked routes to the paywall instead of the picker.
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { cropUri = it }
    }
    cropUri?.let { uri ->
        CoupleBannerCropDialog(
            imageUri = uri,
            onCancel = { cropUri = null },
            onCropped = { bitmap ->
                cropUri = null
                viewModel.setCoupleBanner(bitmap)
            },
        )
    }

    // Tapping the photo circle opens the full couple photo full-screen (tap anywhere to dismiss).
    var viewPhoto by remember { mutableStateOf(false) }
    if (viewPhoto && couple.bannerUrl != null) {
        FullScreenImagePager(
            models = listOf(couple.bannerUrl),
            startIndex = 0,
            contentDescription = "Couple photo",
            onDismiss = { viewPhoto = false },
        )
    }

    // The couple identity is one gradient card (Item 9) — a two-tone blend of both partners' accent
    // colors with their motif avatars + couple name overlaid; the partner label + Remove photo ride
    // a darkened continuation of that same gradient as its [footer] (same as the Combined surface),
    // so there's no separate cream card whose color would show through the photo's overlap zone. A
    // premium uploaded photo (Item 10) floats as a circle straddling the card's top edge, set via
    // the camera affordance — nudged down by [CouplePhotoOverlap] so it clears the photo.
    val photoActive = effectiveBannerSource(couple.bannerUrl, state.bannerUnlocked) is BannerSource.Photo
    Box(modifier = Modifier.fillMaxWidth()) {
        CoupleBanner(
            coupleName = couple.name,
            currentMotifKey = state.currentAvatarMotif,
            currentAccentHex = state.currentAccentColor,
            partnerMotifKey = paired.partner?.avatarMotif,
            partnerAccentHex = paired.partner?.accentColor,
            hasPartner = paired.partner != null,
            bannerUrl = couple.bannerUrl,
            bannerUnlocked = state.bannerUnlocked,
            onPhotoClick = { viewPhoto = true },
            modifier = Modifier.fillMaxWidth(),
            footer = {
                val partnerLabel = when {
                    couple.isAwaitingPartner -> "Waiting for your partner to join…"
                    else -> "Paired with ${paired.partner?.displayName ?: "your partner"}"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        partnerLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    state.bannerError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (state.bannerUnlocked && couple.bannerUrl != null && !state.isBannerWorking) {
                        Text(
                            "Remove photo",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier
                                .clickable(onClick = viewModel::removeCoupleBanner)
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            },
        )
        BannerCameraButton(
            working = state.isBannerWorking,
            onClick = {
                if (!state.bannerUnlocked) onOpenPremium("couple_banner")
                else picker.launch("image/*")
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (photoActive) CouplePhotoOverlap + 8.dp else 8.dp, end = 8.dp),
        )
    }

    if (couple.isAwaitingPartner) {
        val context = LocalContext.current
        PlayfulCard(
            modifier = Modifier.fillMaxWidth(),
            surface = PlayfulSurface.Glass,
            shape = LeafShapes.leafMirrored(22.dp, 9.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Invite code", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    couple.inviteCode,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        val senderName = currentDisplayName ?: "Your partner"
                        val message = "$senderName wants to partner up on Love, Ipon to track our money together!\n\n" +
                            "Open the app and enter this invite code: ${couple.inviteCode}\n\n" +
                            "Or visit: $INVITE_LANDING_URL"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share invite"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Share invite code")
                }
                Text(
                    "Share this code with your partner so they can join.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                OutlinedButton(
                    onClick = viewModel::rotateInviteCode,
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Generate a new code") }
            }
        }
    }

    OutlinedButton(
        onClick = { confirmUnpair = true },
        enabled = !state.isWorking,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Unpair") }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair?") },
            text = {
                Text(
                    "This dissolves the couple for both of you. Shared budgets and goals end, " +
                        "and anything else you shared — accounts, categories, notes — reverts " +
                        "to whoever created it. You keep all your own data.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    viewModel.unpair()
                }) { Text("Unpair") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") }
            },
        )
    }
}

/** The camera affordance overlaid on the couple banner's hero (Item 10) — a translucent circle so
 *  it reads over either the gradient or a photo. Swaps to a small spinner while an upload runs. */
@Composable
private fun BannerCameraButton(working: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        if (working) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = "Set couple photo",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun ErrorBanner(error: PairingError) {
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
    ) {
        Text(
            text = error.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

internal fun PairingError.message(): String = when (this) {
    PairingError.ALREADY_IN_COUPLE -> "You're already in a couple."
    PairingError.INVALID_INVITE_CODE -> "That invite code isn't valid."
    PairingError.COUPLE_FULL -> "That couple already has two members."
    PairingError.OWN_COUPLE -> "You can't join your own couple."
    PairingError.NOT_IN_COUPLE -> "You're not in a couple."
    PairingError.NETWORK -> "No connection. Pairing needs to be online — try again."
    PairingError.UNKNOWN -> "Something went wrong. Please try again."
}
