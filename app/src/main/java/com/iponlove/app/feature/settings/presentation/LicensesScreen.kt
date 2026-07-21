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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * Open-source acknowledgements (v1.6.6 Item 11) — a static list, not build-generated. Update this
 * list if a major dependency (name/license) changes; it isn't scanned from the Gradle graph.
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 6g).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                "Love, Ipon is built with these open-source libraries.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(16.dp))

            OSS_LIBRARIES.forEachIndexed { index, library ->
                LicenseCard(library, index)
                if (index != OSS_LIBRARIES.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LicenseCard(library: OssLibrary, index: Int) {
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
        contentPadding = 16.dp,
    ) {
        Column {
            Text(library.name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                library.license,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
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
