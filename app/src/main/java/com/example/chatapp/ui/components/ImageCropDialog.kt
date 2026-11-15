package com.example.chatapp.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes an image Uri with downsampling and EXIF orientation correction so portrait camera photos
 * and high-resolution gallery images load cleanly without excessive memory usage.
 */
fun decodeOrientedBitmap(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val rawW = bounds.outWidth
    val rawH = bounds.outHeight
    if (rawW <= 0 || rawH <= 0) return null

    var sampleSize = 1
    while ((rawW / sampleSize) > maxDim || (rawH / sampleSize) > maxDim) {
        sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        ?: return null

    val rotationDegrees = try {
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (_: Exception) {
        0f
    }

    if (rotationDegrees == 0f) return decoded
    val matrix = Matrix().apply { postRotate(rotationDegrees) }
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (rotated !== decoded) decoded.recycle()
    return rotated
}

@Composable
fun ImageCropDialog(
    imageUri: Uri,
    title: String,
    outputSizePx: Int = 320,
    showLabelInput: Boolean = false,
    initialLabel: String = "",
    onConfirm: (croppedBitmap: Bitmap, label: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var loadedBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(imageUri) { mutableStateOf(false) }
    var label by remember(imageUri, initialLabel) { mutableStateOf(initialLabel) }

    var zoom by remember(imageUri) { mutableFloatStateOf(1f) }
    var pan by remember(imageUri) { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(imageUri) {
        loadError = false
        val bmp = withContext(Dispatchers.IO) { decodeOrientedBitmap(context, imageUri, maxDim = 1600) }
        if (bmp == null) {
            loadError = true
        } else {
            loadedBitmap = bmp
        }
    }

    fun clampPan(rawPan: Offset, bmp: Bitmap, box: IntSize, scaleFactor: Float): Offset {
        if (box.width <= 0 || box.height <= 0) return Offset.Zero
        val side = box.width.toFloat()
        val imgRatio = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
        // ContentScale.Crop fills the square box cleanly at zoom = 1f
        val baseDispW = if (imgRatio >= 1f) side * imgRatio else side
        val baseDispH = if (imgRatio >= 1f) side else side / imgRatio
        val dispW = baseDispW * scaleFactor
        val dispH = baseDispH * scaleFactor
        val maxPanX = max(0f, (dispW - side) / 2f)
        val maxPanY = max(0f, (dispH - side) / 2f)
        return Offset(
            x = rawPan.x.coerceIn(-maxPanX, maxPanX),
            y = rawPan.y.coerceIn(-maxPanY, maxPanY),
        )
    }

    fun buildCroppedBitmap(bmp: Bitmap): Bitmap {
        val side = boxSize.width.toFloat().coerceAtLeast(1f)
        val imgRatio = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
        val baseDispW = if (imgRatio >= 1f) side * imgRatio else side
        val baseDispH = if (imgRatio >= 1f) side else side / imgRatio
        val dispW = baseDispW * zoom
        val dispH = baseDispH * zoom

        // In centered Crop coordinates inside box [0..side, 0..side], displayed image occupies:
        // left = side/2 - dispW/2 + pan.x, top = side/2 - dispH/2 + pan.y
        val imgLeft = side / 2f - dispW / 2f + pan.x
        val imgTop = side / 2f - dispH / 2f + pan.y

        val normLeft = ((0f - imgLeft) / dispW).coerceIn(0f, 1f)
        val normTop = ((0f - imgTop) / dispH).coerceIn(0f, 1f)
        val normRight = ((side - imgLeft) / dispW).coerceIn(0f, 1f)
        val normBottom = ((side - imgTop) / dispH).coerceIn(0f, 1f)

        val srcX = (normLeft * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
        val srcY = (normTop * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
        val srcW = ((normRight - normLeft) * bmp.width).roundToInt().coerceIn(1, bmp.width - srcX)
        val srcH = ((normBottom - normTop) * bmp.height).roundToInt().coerceIn(1, bmp.height - srcY)

        val cropped = Bitmap.createBitmap(bmp, srcX, srcY, srcW, srcH)
        return if (cropped.width != outputSizePx || cropped.height != outputSizePx) {
            val scaled = Bitmap.createScaledBitmap(cropped, outputSizePx, outputSizePx, true)
            if (scaled !== cropped) cropped.recycle()
            scaled
        } else {
            cropped
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC120E24))
            .clickable(onClick = onDismiss)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        SteppedBox(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clickable(enabled = false) {}
                .testTag("image_crop_dialog"),
            backgroundColor = MothHeaderBg,
            borderColor = LanternAmber,
            cutSize = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        fontFamily = PixelifySans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LanternAmber,
                    )
                    Text(
                        text = "✕",
                        fontSize = 15.sp,
                        color = MutedText,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(4.dp),
                    )
                }

                // Square Interactive Crop Viewport
                val bmp = loadedBitmap
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(MothBgDark)
                        .border(2.dp, LanternAmber)
                        .clipToBounds()
                        .onSizeChanged { size ->
                            boxSize = size
                            if (bmp != null) pan = clampPan(pan, bmp, size, zoom)
                        }
                        .pointerInput(bmp) {
                            if (bmp == null) return@pointerInput
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                val nextZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                val nextPan = clampPan(pan + panChange, bmp, boxSize, nextZoom)
                                zoom = nextZoom
                                pan = nextPan
                            }
                        }
                        .testTag("image_crop_viewport"),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loadError -> {
                            Text(
                                text = "unable to load image",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = WaxSealRed,
                            )
                        }
                        bmp == null -> {
                            Text(
                                text = "loading image...",
                                fontFamily = PixelifySans,
                                fontSize = 12.sp,
                                color = MutedText,
                            )
                        }
                        else -> {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Crop preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoom
                                        scaleY = zoom
                                        translationX = pan.x
                                        translationY = pan.y
                                    },
                            )
                            // Rule-of-thirds pixel crop grid overlay
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val gridColor = Color.White.copy(alpha = 0.28f)
                                val w = size.width
                                val h = size.height
                                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                                drawLine(gridColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.dp.toPx())
                                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                                drawLine(gridColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.dp.toPx())
                            }
                        }
                    }
                }

                // Zoom / Reset Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "drag & pinch • ${"%.1f".format(zoom)}x",
                        fontFamily = PixelifySans,
                        fontSize = 11.sp,
                        color = MutedText,
                        modifier = Modifier.weight(1f),
                    )
                    SteppedButton(
                        text = "-",
                        onClick = {
                            val b = loadedBitmap ?: return@SteppedButton
                            val nextZoom = (zoom - 0.35f).coerceIn(1f, 4f)
                            zoom = nextZoom
                            pan = clampPan(pan, b, boxSize, nextZoom)
                        },
                        backgroundColor = MothBgDark,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                        modifier = Modifier.testTag("crop_zoom_out_button"),
                    )
                    SteppedButton(
                        text = "+",
                        onClick = {
                            val b = loadedBitmap ?: return@SteppedButton
                            val nextZoom = (zoom + 0.35f).coerceIn(1f, 4f)
                            zoom = nextZoom
                            pan = clampPan(pan, b, boxSize, nextZoom)
                        },
                        backgroundColor = MothBgDark,
                        borderColor = TheirBubbleBorder,
                        textColor = TheirBubbleText,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                        modifier = Modifier.testTag("crop_zoom_in_button"),
                    )
                    SteppedButton(
                        text = "reset",
                        onClick = {
                            zoom = 1f
                            pan = Offset.Zero
                        },
                        backgroundColor = MothBgDark,
                        borderColor = MothDivider,
                        textColor = MutedText,
                        cutSize = 2.dp,
                        borderWidth = 1.dp,
                        modifier = Modifier.testTag("crop_reset_button"),
                    )
                }

                // Optional sticker label input
                if (showLabelInput) {
                    SteppedBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        backgroundColor = MothBgDark,
                        borderColor = TheirBubbleBorder,
                        cutSize = 3.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (label.isEmpty()) {
                                Text(
                                    text = "sticker name...",
                                    fontFamily = PixelifySans,
                                    fontSize = 12.sp,
                                    color = PlaceholderText,
                                )
                            }
                            BasicTextField(
                                value = label,
                                onValueChange = { if (it.length <= 24) label = it },
                                textStyle = TextStyle(
                                    fontFamily = PixelifySans,
                                    fontSize = 13.sp,
                                    color = TheirBubbleText,
                                ),
                                cursorBrush = SolidColor(LanternAmber),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("crop_sticker_label_input"),
                            )
                        }
                    }
                }

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SteppedButton(
                        text = "cancel",
                        onClick = onDismiss,
                        backgroundColor = MothBgDark,
                        borderColor = MothDivider,
                        textColor = MutedText,
                        modifier = Modifier.weight(1f).testTag("crop_cancel_button"),
                        cutSize = 3.dp,
                    )
                    SteppedButton(
                        text = "apply crop",
                        onClick = {
                            val b = loadedBitmap ?: return@SteppedButton
                            val out = buildCroppedBitmap(b)
                            onConfirm(out, label.trim())
                        },
                        backgroundColor = MyBubbleFill,
                        borderColor = MyBubbleBorder,
                        textColor = MyBubbleText,
                        modifier = Modifier.weight(1f).testTag("crop_confirm_button"),
                        cutSize = 3.dp,
                    )
                }
            }
        }
    }
}
