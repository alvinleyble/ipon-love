package com.iponlove.app.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** The couple photo is a square box (matches [CoupleBanner]'s small squircle photo slot). */
private const val BANNER_ASPECT = 1f

/** Output crop width (and height, since the frame is square). The uploader downscales again if
 *  needed, but 640 keeps a crisp small photo box without wasting upload bandwidth on a size the
 *  UI never renders larger than. */
private const val OUTPUT_WIDTH = 640

/**
 * A pure-Compose interactive cropper for the couple photo (v1.7.0 Item 10) — hand-rolled rather
 * than pulling a stale/JitPack-only library, keeping CLAUDE.md's "no XML layouts ever" and matching
 * the house style. The picked [imageUri] is shown inside a fixed **square** window the user pans
 * and pinch-zooms; the image is constrained to always cover the window (no empty gaps). [onCropped]
 * receives the framed region as a fresh [Bitmap]; [onCancel] discards.
 */
@Composable
fun CoupleBannerCropDialog(
    imageUri: Uri,
    onCancel: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var source by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(imageUri) { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    // Source→screen transform: a screen point = srcPoint * scale + offset.
    var scale by remember(imageUri) { mutableStateOf(1f) }
    var offsetX by remember(imageUri) { mutableStateOf(0f) }
    var offsetY by remember(imageUri) { mutableStateOf(0f) }
    var initialized by remember(imageUri) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(imageUri) {
        val decoded = withContext(Dispatchers.IO) { decodeSampled(context, imageUri) }
        if (decoded == null) loadFailed = true else source = decoded
    }

    // Initialize the transform to "cover, centered" once both the bitmap and the viewport are known.
    val src = source
    if (src != null && viewport.width > 0 && viewport.height > 0 && !initialized) {
        val cover = coverScale(src.width, src.height, viewport)
        scale = cover
        offsetX = (viewport.width - src.width * cover) / 2f
        offsetY = (viewport.height - src.height * cover) / 2f
        initialized = true
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Drag and pinch to frame your photo",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BANNER_ASPECT)
                    .background(Color.Black)
                    .clipToBounds()
                    .onSizeChanged { viewport = it }
                    .pointerInput(src, viewport) {
                        if (src == null) return@pointerInput
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val cover = coverScale(src.width, src.height, viewport)
                            val newScale = (scale * zoom).coerceIn(cover, cover * 5f)
                            val effZoom = newScale / scale
                            val nx = centroid.x - (centroid.x - offsetX) * effZoom + pan.x
                            val ny = centroid.y - (centroid.y - offsetY) * effZoom + pan.y
                            scale = newScale
                            offsetX = nx.coerceIn(viewport.width - src.width * newScale, 0f)
                            offsetY = ny.coerceIn(viewport.height - src.height * newScale, 0f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    src != null -> {
                        val image = remember(src) { src.asImageBitmap() }
                        Canvas(Modifier.fillMaxSize()) {
                            drawImage(
                                image = image,
                                dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                                dstSize = IntSize(
                                    (src.width * scale).roundToInt(),
                                    (src.height * scale).roundToInt(),
                                ),
                            )
                        }
                    }

                    loadFailed -> Text(
                        "Couldn't open that image.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    else -> CircularProgressIndicator(color = Color.White)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
                TextButton(
                    enabled = src != null && initialized,
                    onClick = {
                        val bitmap = src ?: return@TextButton
                        val vp = viewport
                        val s = scale
                        val ox = offsetX
                        val oy = offsetY
                        scope.launch {
                            val cropped = withContext(Dispatchers.Default) {
                                cropToBanner(bitmap, vp, s, ox, oy)
                            }
                            onCropped(cropped)
                        }
                    },
                ) { Text("Use photo", color = Color.White) }
            }
        }
    }
}

/** The scale that makes [srcW]×[srcH] just cover [viewport] (the larger of the two axis ratios). */
private fun coverScale(srcW: Int, srcH: Int, viewport: IntSize): Float =
    max(viewport.width.toFloat() / srcW, viewport.height.toFloat() / srcH)

/**
 * Renders the on-screen framing into an output [Bitmap] sized to the viewport's true aspect at
 * [OUTPUT_WIDTH]. The display transform (`screen = src*scale + offset`) is scaled up by
 * `k = OUTPUT_WIDTH / viewport.width`; the output canvas clips to the crop window, so only the
 * framed region survives.
 */
private fun cropToBanner(src: Bitmap, viewport: IntSize, scale: Float, offsetX: Float, offsetY: Float): Bitmap {
    val outW = OUTPUT_WIDTH
    val outH = (outW.toFloat() * viewport.height / viewport.width).roundToInt().coerceAtLeast(1)
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    val k = outW.toFloat() / viewport.width
    val dst = RectF(
        offsetX * k,
        offsetY * k,
        (offsetX + src.width * scale) * k,
        (offsetY + src.height * scale) * k,
    )
    canvas.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

/** Decodes [uri] with an inSampleSize that caps the longest edge near 2048px — enough for a crisp
 *  banner crop while keeping a large source photo from OOMing the interactive view. */
private fun decodeSampled(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > 2048) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()
