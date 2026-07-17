package com.iponlove.app.feature.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Open-source acknowledgements (v1.6.6 Item 11) — a static list, not build-generated. Update this
 * list if a major dependency (name/license) changes; it isn't scanned from the Gradle graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open-source licenses") },
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "Love, Ipon is built with these open-source libraries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OSS_LIBRARIES.forEachIndexed { index, library ->
                LicenseCard(library)
                if (index != OSS_LIBRARIES.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LicenseCard(library: OssLibrary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(library.name, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                library.license,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class OssLibrary(val name: String, val license: String)

private val OSS_LIBRARIES = listOf(
    OssLibrary("Jetpack Compose", "Apache License 2.0"),
    OssLibrary("Material Components for Android", "Apache License 2.0"),
    OssLibrary("Room", "Apache License 2.0"),
    OssLibrary("Hilt / Dagger", "Apache License 2.0"),
    OssLibrary("Kotlin Coroutines", "Apache License 2.0"),
    OssLibrary("Jetpack DataStore", "Apache License 2.0"),
    OssLibrary("WorkManager", "Apache License 2.0"),
    OssLibrary("Jetpack Glance", "Apache License 2.0"),
    OssLibrary("Coil", "Apache License 2.0"),
    OssLibrary("supabase-kt", "MIT License"),
    OssLibrary("Ktor", "Apache License 2.0"),
    OssLibrary("Compose Rich Editor (MohamedRejeb)", "MIT License"),
    OssLibrary("Play Billing Library", "Android Software Development Kit License"),
)
