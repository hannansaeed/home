package com.example.chatapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.chatapp.ui.theme.*
import java.io.File

/**
 * Pixel Lantern Sprite: matches the header and avatar lanterns in the screenshots.
 */
@Composable
fun PixelLantern(
    modifier: Modifier = Modifier,
    isLit: Boolean = true,
) {
    Canvas(modifier = modifier.size(20.dp, 24.dp)) {
        val px = size.width / 10f
        val py = size.height / 12f

        val capColor = if (isLit) Color(0xFFA36724) else Color(0xFF332B50)
        val bodyColor = if (isLit) LanternAmber else Color(0xFF433D6B)
        val glowColor = if (isLit) LanternGlow else Color(0xFF5B52A8)

        // Top hanger
        drawRect(capColor, Offset(4 * px, 0 * py), Size(2 * px, 1 * py))
        drawRect(capColor, Offset(3 * px, 1 * py), Size(4 * px, 1 * py))
        drawRect(capColor, Offset(2 * px, 2 * py), Size(6 * px, 1 * py))

        // Glass Body
        drawRect(bodyColor, Offset(1 * px, 3 * py), Size(8 * px, 6 * py))
        // Center Glow
        drawRect(glowColor, Offset(3 * px, 4 * py), Size(4 * px, 4 * py))

        // Bottom cap
        drawRect(capColor, Offset(2 * px, 9 * py), Size(6 * px, 1 * py))
        drawRect(capColor, Offset(3 * px, 10 * py), Size(4 * px, 1 * py))
        drawRect(capColor, Offset(4 * px, 11 * py), Size(2 * px, 1 * py))
    }
}

/**
 * Large Hanging Lantern for Login Screen with string and orbiting glow pixels.
 */
@Composable
fun LargeHangingLantern(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(100.dp, 120.dp)) {
        val w = size.width
        val h = size.height
        val u = w / 20f

        val cordColor = Color(0xFFA36724)
        val capColor = Color(0xFFA36724)
        val bodyColor = LanternAmber
        val glowColor = LanternGlow
        val emberColor = Color(0xFF8C581E)

        // Hanging string from top
        drawRect(cordColor, Offset(9.5f * u, 0f), Size(1 * u, 5 * u))

        // Top cap
        drawRect(capColor, Offset(8 * u, 5 * u), Size(4 * u, 1 * u))
        drawRect(capColor, Offset(7 * u, 6 * u), Size(6 * u, 1 * u))
        drawRect(capColor, Offset(6 * u, 7 * u), Size(8 * u, 1 * u))

        // Lantern Body
        drawRect(bodyColor, Offset(5 * u, 8 * u), Size(10 * u, 9 * u))
        // Inner glowing core
        drawRect(glowColor, Offset(7 * u, 9 * u), Size(6 * u, 7 * u))
        drawRect(Color.White, Offset(8.5f * u, 10.5f * u), Size(3 * u, 4 * u))

        // Bottom cap
        drawRect(capColor, Offset(6 * u, 17 * u), Size(8 * u, 1 * u))
        drawRect(capColor, Offset(7 * u, 18 * u), Size(6 * u, 1 * u))
        drawRect(capColor, Offset(8 * u, 19 * u), Size(4 * u, 1 * u))
        drawRect(capColor, Offset(9 * u, 20 * u), Size(2 * u, 1 * u))

        // Orbiting ember pixels (as seen in Screenshot 3)
        drawRect(emberColor, Offset(2 * u, 8 * u), Size(1.5f * u, 1.5f * u))
        drawRect(emberColor, Offset(16.5f * u, 8 * u), Size(1.5f * u, 1.5f * u))
        drawRect(emberColor, Offset(0.5f * u, 13 * u), Size(1.5f * u, 1.5f * u))
        drawRect(emberColor, Offset(18f * u, 13 * u), Size(1.5f * u, 1.5f * u))
        drawRect(emberColor, Offset(2 * u, 18 * u), Size(1.5f * u, 1.5f * u))
        drawRect(emberColor, Offset(16.5f * u, 18 * u), Size(1.5f * u, 1.5f * u))
        drawRect(emberColor, Offset(6 * u, 21 * u), Size(1.5f * u, 1.5f * u))
        drawRect(emberColor, Offset(12.5f * u, 21 * u), Size(1.5f * u, 1.5f * u))
    }
}

/**
 * 9x5 Pixel Moth Sprite with 2-frame flapping animation.
 */
@Composable
fun PixelMoth(
    modifier: Modifier = Modifier,
    isAnimated: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mothFlap")
    val frame by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flapFrame",
    )

    val isWingsUp = if (isAnimated) frame.toInt() % 2 == 0 else true

    Canvas(modifier = modifier.size(24.dp, 14.dp)) {
        val u = size.width / 9f
        val wingColor = Color(0xFFD3C5FF)
        val bodyColor = Color(0xFFF4E4C1)

        // Center body
        drawRect(bodyColor, Offset(4 * u, 1 * u), Size(1 * u, 3 * u))
        // Antennae
        drawRect(bodyColor, Offset(3.5f * u, 0f), Size(0.5f * u, 1 * u))
        drawRect(bodyColor, Offset(5 * u, 0f), Size(0.5f * u, 1 * u))

        if (isWingsUp) {
            // Frame 1: Wings angled up
            drawRect(wingColor, Offset(0 * u, 0 * u), Size(3 * u, 1 * u))
            drawRect(wingColor, Offset(1 * u, 1 * u), Size(3 * u, 1 * u))
            drawRect(wingColor, Offset(2 * u, 2 * u), Size(2 * u, 1 * u))

            drawRect(wingColor, Offset(6 * u, 0 * u), Size(3 * u, 1 * u))
            drawRect(wingColor, Offset(5 * u, 1 * u), Size(3 * u, 1 * u))
            drawRect(wingColor, Offset(5 * u, 2 * u), Size(2 * u, 1 * u))
        } else {
            // Frame 2: Wings horizontal/flat
            drawRect(wingColor, Offset(0 * u, 1 * u), Size(4 * u, 1 * u))
            drawRect(wingColor, Offset(1 * u, 2 * u), Size(3 * u, 1 * u))
            drawRect(wingColor, Offset(2 * u, 3 * u), Size(2 * u, 1 * u))

            drawRect(wingColor, Offset(5 * u, 1 * u), Size(4 * u, 1 * u))
            drawRect(wingColor, Offset(5 * u, 2 * u), Size(3 * u, 1 * u))
            drawRect(wingColor, Offset(5 * u, 3 * u), Size(2 * u, 1 * u))
        }
    }
}

/**
 * Pixel Wax Seal (used on View-Once cards and Send button).
 */
@Composable
fun PixelWaxSeal(
    modifier: Modifier = Modifier,
    hasArrow: Boolean = false,
) {
    Canvas(modifier = modifier.size(24.dp, 24.dp)) {
        val u = size.width / 8f
        val mainRed = WaxSealRed
        val darkEdge = WaxSealDarkEdge
        val white = Color.White

        // Outer notched red seal
        drawRect(darkEdge, Offset(2 * u, 0 * u), Size(4 * u, 8 * u))
        drawRect(darkEdge, Offset(0 * u, 2 * u), Size(8 * u, 4 * u))
        drawRect(darkEdge, Offset(1 * u, 1 * u), Size(6 * u, 6 * u))

        // Inner fill
        drawRect(mainRed, Offset(2 * u, 1 * u), Size(4 * u, 6 * u))
        drawRect(mainRed, Offset(1 * u, 2 * u), Size(6 * u, 4 * u))

        if (hasArrow) {
            // White pixel arrow (➜)
            drawRect(white, Offset(2 * u, 3 * u), Size(4 * u, 2 * u))
            drawRect(white, Offset(4 * u, 2 * u), Size(1 * u, 4 * u))
            drawRect(white, Offset(5 * u, 2.5f * u), Size(1 * u, 3 * u))
        } else {
            // Center emblem dot
            drawRect(Color(0xFFFFAE9E), Offset(3 * u, 2.5f * u), Size(2 * u, 2 * u))
        }
    }
}

/**
 * Pixel Crescent Moon (as seen in chat background bottom right).
 */
@Composable
fun PixelCrescentMoon(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp, 20.dp)) {
        val u = size.width / 6f
        val v = size.height / 8f
        val moonColor = Color(0xFFFFE8A3)

        drawRect(moonColor, Offset(2 * u, 0 * v), Size(3 * u, 1 * v))
        drawRect(moonColor, Offset(1 * u, 1 * v), Size(2 * u, 1 * v))
        drawRect(moonColor, Offset(0 * u, 2 * v), Size(2 * u, 4 * v))
        drawRect(moonColor, Offset(1 * u, 6 * v), Size(2 * u, 1 * v))
        drawRect(moonColor, Offset(2 * u, 7 * v), Size(3 * u, 1 * v))
    }
}

/**
 * Pixel Firefly for reaction chip.
 */
@Composable
fun PixelFirefly(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(12.dp, 12.dp)) {
        val u = size.width / 4f
        val color = LanternAmber
        val center = Color.White

        drawRect(color, Offset(1 * u, 0 * u), Size(2 * u, 4 * u))
        drawRect(color, Offset(0 * u, 1 * u), Size(4 * u, 2 * u))
        drawRect(center, Offset(1.5f * u, 1.5f * u), Size(1 * u, 1 * u))
    }
}

/**
 * 40px (or custom size) Stepped-Corner Pixel Avatar with simple face (eyes + mouth line).
 */
@Composable
fun PixelFaceAvatar(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, SteppedCornerShape(4.dp))
            .border(2.dp, Color(0xFF1A1633), SteppedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.6f, size * 0.4f)) {
            val eyeColor = Color(0xFF1A1633)
            val w = this.size.width
            val h = this.size.height
            // Left eye
            drawRect(eyeColor, Offset(w * 0.125f, h * 0.125f), Size(w * 0.125f, h * 0.25f))
            // Right eye
            drawRect(eyeColor, Offset(w * 0.71f, h * 0.125f), Size(w * 0.125f, h * 0.25f))
            // Mouth
            drawRect(eyeColor, Offset(w * 0.33f, h * 0.68f), Size(w * 0.30f, h * 0.16f))
        }
    }
}

/**
 * Displays a custom avatar photo if set and present on disk, otherwise falls back to PixelFaceAvatar.
 */
@Composable
fun UserAvatar(
    avatarPath: String?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val hasPhoto = !avatarPath.isNullOrBlank() && File(avatarPath).exists()
    if (hasPhoto && avatarPath != null) {
        val shape = SteppedCornerShape(4.dp)
        AsyncImage(
            model = File(avatarPath),
            contentDescription = "User avatar",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
                .border(2.dp, TheirBubbleBorder, shape),
        )
    } else {
        PixelFaceAvatar(color = color, modifier = modifier, size = size)
    }
}

/**
 * Pixel Sticker Square Icon for chat input bar (replaces emoji mode buttons).
 */
@Composable
fun PixelStickerIcon(
    tint: Color = LanternAmber,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val u = size.width / 8f
        // Outer sticker frame with folded bottom-right corner
        drawRect(tint, Offset(0f, 0f), Size(8 * u, 1 * u))
        drawRect(tint, Offset(0f, 0f), Size(1 * u, 8 * u))
        drawRect(tint, Offset(7 * u, 0f), Size(1 * u, 5 * u))
        drawRect(tint, Offset(0f, 7 * u), Size(5 * u, 1 * u))
        // Folded corner
        drawRect(tint, Offset(5 * u, 5 * u), Size(2 * u, 1 * u))
        drawRect(tint, Offset(5 * u, 5 * u), Size(1 * u, 2 * u))
        drawRect(tint, Offset(6 * u, 6 * u), Size(1 * u, 1 * u))
        // Little pixel smile inside
        drawRect(tint, Offset(2.2f * u, 2.5f * u), Size(1 * u, 1.2f * u))
        drawRect(tint, Offset(4.8f * u, 2.5f * u), Size(1 * u, 1.2f * u))
        drawRect(tint, Offset(2.5f * u, 4.5f * u), Size(2.5f * u, 1 * u))
    }
}

/**
 * Scattered Pixel Stars (2-3px squares).
 */
@Composable
fun PixelStarfield(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val starPositions = listOf(
            Offset(0.15f * w, 0.08f * h) to StarYellow,
            Offset(0.70f * w, 0.07f * h) to MutedText,
            Offset(0.85f * w, 0.12f * h) to StarYellow,
            Offset(0.08f * w, 0.22f * h) to MutedText,
            Offset(0.78f * w, 0.23f * h) to MutedText,
            Offset(0.28f * w, 0.35f * h) to StarYellow,
            Offset(0.75f * w, 0.42f * h) to StarYellow,
            Offset(0.18f * w, 0.54f * h) to MutedText,
            Offset(0.86f * w, 0.45f * h) to MutedText,
            Offset(0.29f * w, 0.57f * h) to StarYellow,
            Offset(0.63f * w, 0.64f * h) to StarYellow,
            Offset(0.88f * w, 0.63f * h) to StarYellow,
            Offset(0.77f * w, 0.74f * h) to MutedText,
            Offset(0.22f * w, 0.88f * h) to StarYellow,
            Offset(0.82f * w, 0.90f * h) to MutedText,
        )

        for ((pos, color) in starPositions) {
            drawRect(color, pos, Size(3.dp.toPx(), 3.dp.toPx()))
        }
    }
}

/**
 * Row of 10 burning ember squares for disappearing messages.
 */
@Composable
fun DisappearingEmbers(
    total: Int = 10,
    litCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until total) {
            val isLit = i < litCount
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (isLit) LanternAmber else MothDivider),
            )
        }
    }
}
