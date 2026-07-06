package com.iponlove.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * Full-screen image viewer shared by receipt and note-image thumbnails. [model] is anything
 * Coil accepts (remote URL string or local [java.io.File]); tap anywhere to dismiss.
 */
@Composable
fun FullScreenImageDialog(
    model: Any,
    contentDescription: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Full-screen viewer over several images, opened at [startIndex] and swipeable left/right.
 * Each [models] element is anything Coil accepts (URL string or local [java.io.File]); tap
 * anywhere to dismiss. Falls back gracefully when there's only one image.
 */
@Composable
fun FullScreenImagePager(
    models: List<Any>,
    startIndex: Int,
    contentDescription: String,
    onDismiss: () -> Unit,
) {
    if (models.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, models.lastIndex),
        pageCount = { models.size },
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
        ) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = models[page],
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
