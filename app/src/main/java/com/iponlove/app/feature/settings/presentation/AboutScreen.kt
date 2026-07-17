package com.iponlove.app.feature.settings.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.iponlove.app.BuildConfig

/** About sub-screen (v1.6.6 Item 11) — version, legal links, licenses, rate & share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
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
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = {
                    Text(
                        if (BuildConfig.IS_BETA_BUILD) "${BuildConfig.VERSION_NAME} (Beta)"
                        else BuildConfig.VERSION_NAME,
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Terms of Service") },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_OF_SERVICE_URL)),
                    )
                }),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Open-source licenses") },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onOpenLicenses),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Rate this app") },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = { openPlayStore(context) }),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Share this app") },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = { shareApp(context) }),
            )
            HorizontalDivider()
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
