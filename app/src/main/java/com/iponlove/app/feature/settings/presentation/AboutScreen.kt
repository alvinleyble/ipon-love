package com.iponlove.app.feature.settings.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iponlove.app.BuildConfig
import com.iponlove.app.core.ui.SettingsRow
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/** About sub-screen (v1.6.6 Item 11) — version, legal links, licenses, rate & share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalPlayfulColors.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textSecondary,
                ),
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            SettingsRow(
                headline = "Version",
                supporting = if (BuildConfig.IS_BETA_BUILD) "${BuildConfig.VERSION_NAME} (Beta)"
                else BuildConfig.VERSION_NAME,
                index = 0,
            )
            SettingsRow(
                headline = "Terms of Service",
                index = 1,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_OF_SERVICE_URL)),
                    )
                },
            )
            SettingsRow(
                headline = "Open-source licenses",
                index = 2,
                onClick = onOpenLicenses,
            )
            SettingsRow(
                headline = "Rate this app",
                index = 3,
                onClick = { openPlayStore(context) },
            )
            SettingsRow(
                headline = "Share this app",
                index = 4,
                onClick = { shareApp(context) },
            )
        }
    }
}

/** market:// deep-links straight into the Play Store app; falls back to the web listing. */
private fun openPlayStore(context: android.content.Context) {
    val packageName = BuildConfig.APPLICATION_ID
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")),
        )
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(playStoreWebUrl(packageName))),
        )
    }
}

private fun shareApp(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Check out Love, Ipon — a budget tracker for couples: ${playStoreWebUrl(BuildConfig.APPLICATION_ID)}",
        )
    }
    context.startActivity(Intent.createChooser(intent, "Share Love, Ipon"))
}

private fun playStoreWebUrl(packageName: String) = "https://play.google.com/store/apps/details?id=$packageName"

private const val TERMS_OF_SERVICE_URL = "https://alvinleyble.github.io/ipon-love-legal/terms-of-service.html"
