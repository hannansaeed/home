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
    val bloom = isBloomTheme
    Canvas(modifier = modifier.size(20.dp, 24.dp)) {
        if (bloom) {
            val u = size.width / 10f
            val v = size.height / 12f
            val petalColor = if (isLit) Color(0xFFFFD147) else Color(0xFF9EB58F)
            val centerColor = if (isLit) Color(0xFF5D3B1A) else Color(0xFF687E5B)

            // Pixel sunflower petals
            drawRect(petalColor, Offset(2 * u, 1 * v), Size(6 * u, 2 * v))
            drawRect(petalColor, Offset(2 * u, 9 * v), Size(6 * u, 2 * v))
            drawRect(petalColor, Offset(1 * u, 2 * v), Size(2 * u, 8 * v))
            drawRect(petalColor, Offset(7 * u, 2 * v), Size(2 * u, 8 * v))
            // Seed center
            drawRect(centerColor, Offset(3 * u, 3 * v), Size(4 * u, 6 * v))
        } else {
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
}

/**
 * Large Hanging Lantern (or Blooming Pixel Sunflower in Bloom Theme) for Login & Splash screens.
 */
@Composable
fun LargeHangingLantern(
    modifier: Modifier = Modifier,
) {
    val bloom = isBloomTheme
    Canvas(modifier = modifier.size(100.dp, 120.dp)) {
        val w = size.width
        val u = w / 20f

        if (bloom) {
            val petalYellow = Color(0xFFFFD147)
            val petalWarm = Color(0xFFF6B93B)
            val coreBrown = Color(0xFF5D3B1A)
            val stemGreen = Color(0xFF478A3B)
            val leafLight = Color(0xFF72AC54)
            val pinkPetal = Color(0xFFF482AA)
            val skyPetal = Color(0xFFBDE2FA)

            // Leafy stem below flower
            drawRect(stemGreen, Offset(9 * u, 13 * u), Size(2 * u, 9 * u))
            drawRect(leafLight, Offset(5 * u, 16 * u), Size(4 * u, 2 * u))
            drawRect(stemGreen, Offset(6 * u, 17 * u), Size(3 * u, 1 * u))
            drawRect(leafLight, Offset(11 * u, 18 * u), Size(4 * u, 2 * u))
            drawRect(stemGreen, Offset(11 * u, 19 * u), Size(3 * u, 1 * u))

            // Stepped sunflower blossom head
            drawRect(petalYellow, Offset(6 * u, 3 * u), Size(8 * u, 11 * u))
            drawRect(petalYellow, Offset(4 * u, 5 * u), Size(12 * u, 7 * u))
            drawRect(petalWarm, Offset(7 * u, 4 * u), Size(6 * u, 9 * u))
            drawRect(coreBrown, Offset(7 * u, 5.5f * u), Size(6 * u, 6 * u))
            drawRect(Color(0xFF7B5028), Offset(8.5f * u, 7 * u), Size(3 * u, 3 * u))

            // Orbiting pastel petals
            drawRect(pinkPetal, Offset(1.5f * u, 4 * u), Size(1.5f * u, 1.5f * u))
            drawRect(skyPetal, Offset(17 * u, 4.5f * u), Size(1.5f * u, 1.5f * u))
            drawRect(skyPetal, Offset(2 * u, 12 * u), Size(1.5f * u, 1.5f * u))
            drawRect(pinkPetal, Offset(16.5f * u, 12 * u), Size(1.5f * u, 1.5f * u))
        } else {
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

            // Orbiting ember pixels
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
}

/**
 * 9x5 Pixel Moth Sprite (or Flapping Pixel Bee in Bloom Theme) with 2-frame animation.
 */
@Composable
fun PixelMoth(
    modifier: Modifier = Modifier,
    isAnimated: Boolean = true,
) {
    val bloom = isBloomTheme
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
        if (bloom) {
            val yellow = Color(0xFFFFD147)
            val stripe = Color(0xFF241D15)
            val wing = Color(0xFFBDE2FA)
            // Pixel bee body facing right
            drawRect(yellow, Offset(2 * u, 2 * u), Size(5 * u, 2.4f * u))
            drawRect(stripe, Offset(3.2f * u, 2 * u), Size(0.9f * u, 2.4f * u))
            drawRect(stripe, Offset(5.1f * u, 2 * u), Size(0.9f * u, 2.4f * u))
            drawRect(stripe, Offset(7.4f * u, 2.6f * u), Size(0.7f * u, 0.9f * u)) // eye/stinger accent
            // Wings flutter above body
            if (isWingsUp) {
                drawRect(wing, Offset(2.8f * u, 0.3f * u), Size(2.2f * u, 1.7f * u))
                drawRect(wing, Offset(4.4f * u, 0.7f * u), Size(2.0f * u, 1.3f * u))
            } else {
                drawRect(wing, Offset(2.2f * u, 1.0f * u), Size(2.5f * u, 1.0f * u))
                drawRect(wing, Offset(4.8f * u, 1.1f * u), Size(2.0f * u, 0.9f * u))
            }
        } else {
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
}

/**
 * Pixel Wax Seal (or Pink Blossom Seal in Bloom Theme).
 */
@Composable
fun PixelWaxSeal(
    modifier: Modifier = Modifier,
    hasArrow: Boolean = false,
) {
    val bloom = isBloomTheme
    Canvas(modifier = modifier.size(24.dp, 24.dp)) {
        val u = size.width / 8f
        if (bloom) {
            val petalPink = Color(0xFFF482AA)
            val deepRose = Color(0xFFB83E6F)
            val highlight = Color(0xFFFFFDF7)

            // Outer notched rose blossom
            drawRect(deepRose, Offset(2 * u, 0 * u), Size(4 * u, 8 * u))
            drawRect(deepRose, Offset(0 * u, 2 * u), Size(8 * u, 4 * u))
            drawRect(deepRose, Offset(1 * u, 1 * u), Size(6 * u, 6 * u))

            // Bright pink inner blossom petals
            drawRect(petalPink, Offset(2 * u, 0 * u), Size(4 * u, 6.5f * u))
            drawRect(petalPink, Offset(0.5f * u, 1.5f * u), Size(6.5f * u, 4.5f * u))
            drawRect(petalPink, Offset(1 * u, 1 * u), Size(5 * u, 5 * u))

            // Delicate white blossom swirl / arrow highlights
            drawRect(highlight, Offset(2.2f * u, 2.2f * u), Size(2.4f * u, 1 * u))
            drawRect(highlight, Offset(3.4f * u, 3.6f * u), Size(2.4f * u, 1 * u))
            drawRect(highlight, Offset(3.6f * u, 4.6f * u), Size(1.0f * u, 1.8f * u))
        } else {
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
                // White pixel arrow
                drawRect(white, Offset(2 * u, 3 * u), Size(4 * u, 2 * u))
                drawRect(white, Offset(4 * u, 2 * u), Size(1 * u, 4 * u))
                drawRect(white, Offset(5 * u, 2.5f * u), Size(1 * u, 3 * u))
            } else {
                // Center emblem dot
                drawRect(Color(0xFFFFAE9E), Offset(3 * u, 2.5f * u), Size(2 * u, 2 * u))
            }
        }
    }
}

/**
 * Pixel Crescent Moon (or Golden 4-Petal Blossom in Bloom Theme).
 */
@Composable
fun PixelCrescentMoon(
    modifier: Modifier = Modifier,
) {
    val bloom = isBloomTheme
    Canvas(modifier = modifier.size(16.dp, 20.dp)) {
        if (bloom) {
            val u = size.width / 6f
            val gold = Color(0xFFFFD147)
            drawRect(gold, Offset(2 * u, 1.5f * u), Size(2 * u, 6 * u))
            drawRect(gold, Offset(0.5f * u, 3 * u), Size(5 * u, 2.5f * u))
        } else {
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
}

/**
 * Pixel Firefly (or Tiny Pixel Bee in Bloom Theme) for reaction chip.
 */
@Composable
fun PixelFirefly(
    modifier: Modifier = Modifier,
) {
    val bloom = isBloomTheme
    Canvas(modifier = modifier.size(12.dp, 12.dp)) {
        if (bloom) {
            val u = size.width / 6f
            val wing = Color(0xFFBDE2FA)
            val yellow = Color(0xFFFFD147)
            val stripe = Color(0xFF241D15)
            // Tiny wings
            drawRect(wing, Offset(1.2f * u, 0.8f * u), Size(3.6f * u, 1.4f * u))
            // Striped yellow/black body
            drawRect(yellow, Offset(0.8f * u, 2.2f * u), Size(4.4f * u, 2.6f * u))
            drawRect(stripe, Offset(1.8f * u, 2.2f * u), Size(0.9f * u, 2.6f * u))
            drawRect(stripe, Offset(3.5f * u, 2.2f * u), Size(0.9f * u, 2.6f * u))
        } else {
            val u = size.width / 4f
            val color = LanternAmber
            val center = Color.White

            drawRect(color, Offset(1 * u, 0 * u), Size(2 * u, 4 * u))
            drawRect(color, Offset(0 * u, 1 * u), Size(4 * u, 2 * u))
            drawRect(center, Offset(1.5f * u, 1.5f * u), Size(1 * u, 1 * u))
        }
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
 * Outlined Pixel Back Arrow (crisp Canvas blocks — avoids font glyph issues on older Android).
 */
@Composable
fun PixelBackArrowIcon(
    tint: Color = LanternAmber,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val u = size.width / 10f
        // Horizontal shaft
        drawRect(tint, Offset(2 * u, 4.5f * u), Size(7 * u, 1.1f * u))
        // Upper diagonal head
        drawRect(tint, Offset(2 * u, 3.5f * u), Size(1.2f * u, 1 * u))
        drawRect(tint, Offset(3 * u, 2.5f * u), Size(1.2f * u, 1 * u))
        drawRect(tint, Offset(4 * u, 1.5f * u), Size(1.2f * u, 1 * u))
        // Lower diagonal head
        drawRect(tint, Offset(2 * u, 5.6f * u), Size(1.2f * u, 1 * u))
        drawRect(tint, Offset(3 * u, 6.6f * u), Size(1.2f * u, 1 * u))
        drawRect(tint, Offset(4 * u, 7.6f * u), Size(1.2f * u, 1 * u))
        // Sharp tip
        drawRect(tint, Offset(1 * u, 4.5f * u), Size(1.2f * u, 1.1f * u))
    }
}

/**
 * Outlined Pixel Magnifying Glass Search Icon.
 */
@Composable
fun PixelSearchIcon(
    tint: Color = LanternAmber,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val u = size.width / 10f
        // Stepped octagonal glass outline
        drawRect(tint, Offset(2 * u, 1 * u), Size(4 * u, 1 * u)) // top
        drawRect(tint, Offset(2 * u, 6 * u), Size(4 * u, 1 * u)) // bottom
        drawRect(tint, Offset(1 * u, 2 * u), Size(1 * u, 4 * u)) // left
        drawRect(tint, Offset(6 * u, 2 * u), Size(1 * u, 4 * u)) // right
        // Handle (bottom-right diagonal pixels)
        drawRect(tint, Offset(6.2f * u, 6.2f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(7.2f * u, 7.2f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(8.2f * u, 8.2f * u), Size(1.2f * u, 1.2f * u))
    }
}

/**
 * Outlined Pixel Microphone Icon (for Voice Note & Mute/Unmute buttons).
 */
@Composable
fun PixelMicIcon(
    tint: Color = LanternAmber,
    muted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val u = size.width / 10f
        // Capsule outline
        drawRect(tint, Offset(3.5f * u, 0.8f * u), Size(3 * u, 1 * u))
        drawRect(tint, Offset(3 * u, 1.8f * u), Size(1 * u, 3.6f * u))
        drawRect(tint, Offset(6 * u, 1.8f * u), Size(1 * u, 3.6f * u))
        drawRect(tint, Offset(3.5f * u, 5.2f * u), Size(3 * u, 0.9f * u))
        // Outer cradle arms
        drawRect(tint, Offset(1.5f * u, 4 * u), Size(1 * u, 2.2f * u))
        drawRect(tint, Offset(7.5f * u, 4 * u), Size(1 * u, 2.2f * u))
        drawRect(tint, Offset(2.3f * u, 6.2f * u), Size(5.4f * u, 0.9f * u))
        // Stem & base
        drawRect(tint, Offset(4.5f * u, 7.1f * u), Size(1 * u, 1.5f * u))
        drawRect(tint, Offset(3 * u, 8.6f * u), Size(4 * u, 0.9f * u))
        if (muted) {
            for (i in 1..8) {
                drawRect(tint, Offset(i * u, (9 - i) * u), Size(1.1f * u, 1.1f * u))
            }
        }
    }
}

/**
 * Outlined Pixel Phone Handset Icon (for Voice Call buttons).
 */
@Composable
fun PixelPhoneIcon(
    tint: Color = LanternAmber,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val u = size.width / 10f
        // Top earpiece outlined box
        drawRect(tint, Offset(1.5f * u, 1 * u), Size(2.5f * u, 1 * u))
        drawRect(tint, Offset(1 * u, 2 * u), Size(1 * u, 2 * u))
        drawRect(tint, Offset(3.5f * u, 2 * u), Size(1 * u, 1.5f * u))
        // Curved stepped neck
        drawRect(tint, Offset(2 * u, 4 * u), Size(1 * u, 1.5f * u))
        drawRect(tint, Offset(3 * u, 5.2f * u), Size(1.3f * u, 1.3f * u))
        drawRect(tint, Offset(4.3f * u, 6.2f * u), Size(1.5f * u, 1.3f * u))
        // Bottom mouthpiece outlined box
        drawRect(tint, Offset(5.8f * u, 7 * u), Size(1.2f * u, 1.5f * u))
        drawRect(tint, Offset(6.5f * u, 5.5f * u), Size(2 * u, 1 * u))
        drawRect(tint, Offset(8 * u, 6.5f * u), Size(1 * u, 2 * u))
        drawRect(tint, Offset(6.2f * u, 8.2f * u), Size(2.3f * u, 1 * u))
    }
}

/**
 * Outlined Pixel Speaker Icon.
 */
@Composable
fun PixelSpeakerIcon(
    tint: Color = LanternAmber,
    speakerOn: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val u = size.width / 10f
        // Speaker body outline
        drawRect(tint, Offset(1 * u, 3.5f * u), Size(2 * u, 0.9f * u))
        drawRect(tint, Offset(1 * u, 5.6f * u), Size(2 * u, 0.9f * u))
        drawRect(tint, Offset(1 * u, 3.5f * u), Size(0.9f * u, 3 * u))
        // Cone
        drawRect(tint, Offset(3 * u, 2.5f * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(4 * u, 1.5f * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(3 * u, 6.5f * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(4 * u, 7.5f * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(5 * u, 1.5f * u), Size(0.9f * u, 7 * u))
        if (speakerOn) {
            drawRect(tint, Offset(7 * u, 3.5f * u), Size(0.9f * u, 3 * u))
            drawRect(tint, Offset(8.5f * u, 2.2f * u), Size(0.9f * u, 5.6f * u))
        }
    }
}

/**
 * Outlined Pixel Camera Icon.
 */
@Composable
fun PixelCameraIcon(
    tint: Color = TheirBubbleText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        // Top prism bump
        drawRect(tint, Offset(3.5f * u, 1.5f * u), Size(3 * u, 1 * u))
        // Frame
        drawRect(tint, Offset(1 * u, 2.5f * u), Size(8 * u, 1 * u))
        drawRect(tint, Offset(1 * u, 7.5f * u), Size(8 * u, 1 * u))
        drawRect(tint, Offset(1 * u, 2.5f * u), Size(1 * u, 6 * u))
        drawRect(tint, Offset(8 * u, 2.5f * u), Size(1 * u, 6 * u))
        // Lens square outline
        drawRect(tint, Offset(4 * u, 4.2f * u), Size(2 * u, 0.9f * u))
        drawRect(tint, Offset(4 * u, 6.1f * u), Size(2 * u, 0.9f * u))
        drawRect(tint, Offset(3.5f * u, 4.5f * u), Size(0.9f * u, 2.2f * u))
        drawRect(tint, Offset(5.6f * u, 4.5f * u), Size(0.9f * u, 2.2f * u))
    }
}

/**
 * Outlined Pixel Photo Gallery Icon.
 */
@Composable
fun PixelPhotoIcon(
    tint: Color = TheirBubbleText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        // Frame
        drawRect(tint, Offset(1 * u, 1.5f * u), Size(8 * u, 1 * u))
        drawRect(tint, Offset(1 * u, 7.5f * u), Size(8 * u, 1 * u))
        drawRect(tint, Offset(1 * u, 1.5f * u), Size(1 * u, 7 * u))
        drawRect(tint, Offset(8 * u, 1.5f * u), Size(1 * u, 7 * u))
        // Sun dot
        drawRect(tint, Offset(3 * u, 3.3f * u), Size(1.3f * u, 1.3f * u))
        // Mountain steps
        drawRect(tint, Offset(2.2f * u, 6.2f * u), Size(1.5f * u, 1 * u))
        drawRect(tint, Offset(3.5f * u, 5.2f * u), Size(1.5f * u, 1 * u))
        drawRect(tint, Offset(4.8f * u, 4.5f * u), Size(1.5f * u, 1 * u))
        drawRect(tint, Offset(6.2f * u, 5.5f * u), Size(1.5f * u, 1 * u))
    }
}

/**
 * Outlined Pixel File / Document Icon.
 */
@Composable
fun PixelFileIcon(
    tint: Color = TheirBubbleText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(2 * u, 1 * u), Size(4 * u, 1 * u))
        drawRect(tint, Offset(2 * u, 1 * u), Size(1 * u, 8 * u))
        drawRect(tint, Offset(2 * u, 8 * u), Size(6 * u, 1 * u))
        drawRect(tint, Offset(7 * u, 3.5f * u), Size(1 * u, 5.5f * u))
        // Folded corner
        drawRect(tint, Offset(5.5f * u, 1 * u), Size(1 * u, 2.5f * u))
        drawRect(tint, Offset(5.5f * u, 3 * u), Size(2.5f * u, 1 * u))
        // Text lines
        drawRect(tint, Offset(3.6f * u, 4.8f * u), Size(2.8f * u, 0.8f * u))
        drawRect(tint, Offset(3.6f * u, 6.2f * u), Size(2.2f * u, 0.8f * u))
    }
}

/**
 * Outlined Pixel Seal / View-Once Eye Icon.
 */
@Composable
fun PixelSealIcon(
    tint: Color = TheirBubbleText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(2.5f * u, 2.5f * u), Size(5 * u, 1 * u))
        drawRect(tint, Offset(2.5f * u, 6.5f * u), Size(5 * u, 1 * u))
        drawRect(tint, Offset(1 * u, 3.5f * u), Size(1.5f * u, 1 * u))
        drawRect(tint, Offset(7.5f * u, 3.5f * u), Size(1.5f * u, 1 * u))
        drawRect(tint, Offset(1 * u, 5.5f * u), Size(1.5f * u, 1 * u))
        drawRect(tint, Offset(7.5f * u, 5.5f * u), Size(1.5f * u, 1 * u))
        drawRect(tint, Offset(0.5f * u, 4.5f * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(8.5f * u, 4.5f * u), Size(1 * u, 1 * u))
        // Pupil outline
        drawRect(tint, Offset(4 * u, 4.2f * u), Size(2 * u, 1.6f * u))
    }
}

/**
 * Outlined Pixel Action Icons (Reply, Forward, Pin, Info, Edit, Trash, Block).
 */
@Composable
fun PixelReplyIcon(tint: Color = TheirBubbleText, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(1.5f * u, 4 * u), Size(5.5f * u, 1 * u))
        drawRect(tint, Offset(2.5f * u, 3 * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(3.5f * u, 2 * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(2.5f * u, 5 * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(3.5f * u, 6 * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(7 * u, 4 * u), Size(1 * u, 3.5f * u))
    }
}

@Composable
fun PixelForwardIcon(tint: Color = TheirBubbleText, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(3 * u, 4 * u), Size(5.5f * u, 1 * u))
        drawRect(tint, Offset(6.5f * u, 3 * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(5.5f * u, 2 * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(6.5f * u, 5 * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(5.5f * u, 6 * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(2 * u, 4 * u), Size(1 * u, 3.5f * u))
    }
}

@Composable
fun PixelPinIcon(tint: Color = LanternAmber, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(2.5f * u, 1.5f * u), Size(5 * u, 1 * u))
        drawRect(tint, Offset(3 * u, 2.5f * u), Size(1 * u, 2.5f * u))
        drawRect(tint, Offset(6 * u, 2.5f * u), Size(1 * u, 2.5f * u))
        drawRect(tint, Offset(2 * u, 5 * u), Size(6 * u, 1 * u))
        drawRect(tint, Offset(4.5f * u, 6 * u), Size(1 * u, 3 * u))
    }
}

@Composable
fun PixelInfoIcon(tint: Color = SecureGreen, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(2 * u, 1 * u), Size(6 * u, 1 * u))
        drawRect(tint, Offset(2 * u, 8 * u), Size(6 * u, 1 * u))
        drawRect(tint, Offset(1 * u, 2 * u), Size(1 * u, 6 * u))
        drawRect(tint, Offset(8 * u, 2 * u), Size(1 * u, 6 * u))
        drawRect(tint, Offset(4.5f * u, 3 * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(4.5f * u, 4.8f * u), Size(1 * u, 2.4f * u))
    }
}

@Composable
fun PixelEditIcon(tint: Color = LanternAmber, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(6.5f * u, 1.5f * u), Size(2 * u, 1.5f * u))
        drawRect(tint, Offset(5.2f * u, 2.8f * u), Size(1.5f * u, 1.5f * u))
        drawRect(tint, Offset(3.9f * u, 4.1f * u), Size(1.5f * u, 1.5f * u))
        drawRect(tint, Offset(2.6f * u, 5.4f * u), Size(1.5f * u, 1.5f * u))
        drawRect(tint, Offset(1.5f * u, 7 * u), Size(1.5f * u, 1.5f * u))
        drawRect(tint, Offset(1.5f * u, 8.5f * u), Size(7 * u, 0.8f * u))
    }
}

@Composable
fun PixelTrashIcon(tint: Color = MutedText, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(3.5f * u, 1 * u), Size(3 * u, 1 * u))
        drawRect(tint, Offset(1.5f * u, 2.2f * u), Size(7 * u, 1 * u))
        drawRect(tint, Offset(2.2f * u, 3.2f * u), Size(1 * u, 5.5f * u))
        drawRect(tint, Offset(6.8f * u, 3.2f * u), Size(1 * u, 5.5f * u))
        drawRect(tint, Offset(2.2f * u, 8 * u), Size(5.6f * u, 1 * u))
        drawRect(tint, Offset(4.5f * u, 4.2f * u), Size(1 * u, 3 * u))
    }
}

@Composable
fun PixelBlockIcon(tint: Color = WaxSealRed, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(2.5f * u, 1 * u), Size(5 * u, 1 * u))
        drawRect(tint, Offset(2.5f * u, 8 * u), Size(5 * u, 1 * u))
        drawRect(tint, Offset(1 * u, 2.5f * u), Size(1 * u, 5 * u))
        drawRect(tint, Offset(8 * u, 2.5f * u), Size(1 * u, 5 * u))
        for (i in 2..7) {
            drawRect(tint, Offset(i * u, i * u), Size(1 * u, 1 * u))
        }
    }
}

/**
 * Outlined Pixel Up Arrow (replaces ↑ glyph that can fail on older Android).
 */
@Composable
fun PixelArrowUpIcon(
    tint: Color = LanternAmber,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(4.4f * u, 2 * u), Size(1.2f * u, 7 * u))
        drawRect(tint, Offset(4.4f * u, 1 * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(3.2f * u, 2 * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(5.6f * u, 2 * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(2 * u, 3.2f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(6.8f * u, 3.2f * u), Size(1.2f * u, 1.2f * u))
    }
}

/**
 * Outlined Pixel Down Arrow (replaces ↓ glyph that can fail on older Android).
 */
@Composable
fun PixelArrowDownIcon(
    tint: Color = LanternAmber,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(4.4f * u, 1 * u), Size(1.2f * u, 7 * u))
        drawRect(tint, Offset(4.4f * u, 7.8f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(3.2f * u, 6.8f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(5.6f * u, 6.8f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(2 * u, 5.6f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(6.8f * u, 5.6f * u), Size(1.2f * u, 1.2f * u))
    }
}

/**
 * Outlined Pixel Outgoing Diagonal Arrow (top-right) for call logs.
 */
@Composable
fun PixelArrowOutIcon(
    tint: Color = LanternAmber,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(12.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(5 * u, 1.5f * u), Size(3.5f * u, 1.1f * u))
        drawRect(tint, Offset(7.4f * u, 1.5f * u), Size(1.1f * u, 3.5f * u))
        for (i in 0..5) {
            drawRect(tint, Offset((2 + i) * u, (7.2f - i) * u), Size(1.2f * u, 1.2f * u))
        }
    }
}

/**
 * Outlined Pixel Incoming Diagonal Arrow (bottom-left) for call logs.
 */
@Composable
fun PixelArrowInIcon(
    tint: Color = SecureGreen,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(12.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(1.5f * u, 7.4f * u), Size(3.5f * u, 1.1f * u))
        drawRect(tint, Offset(1.5f * u, 5f * u), Size(1.1f * u, 3.5f * u))
        for (i in 0..5) {
            drawRect(tint, Offset((2.2f + i) * u, (7f - i) * u), Size(1.2f * u, 1.2f * u))
        }
    }
}

/**
 * Pixel Play Triangle for voice notes & video tiles.
 */
@Composable
fun PixelPlayIcon(
    tint: Color = MothBgDark,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(12.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(2.5f * u, 1.5f * u), Size(1.5f * u, 7 * u))
        drawRect(tint, Offset(4f * u, 2.3f * u), Size(1.5f * u, 5.4f * u))
        drawRect(tint, Offset(5.5f * u, 3.2f * u), Size(1.5f * u, 3.6f * u))
        drawRect(tint, Offset(7f * u, 4.2f * u), Size(1.5f * u, 1.6f * u))
    }
}

/**
 * Pixel Pause Bars for voice notes.
 */
@Composable
fun PixelPauseIcon(
    tint: Color = MothBgDark,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(12.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(2.3f * u, 1.8f * u), Size(2 * u, 6.4f * u))
        drawRect(tint, Offset(5.7f * u, 1.8f * u), Size(2 * u, 6.4f * u))
    }
}

/**
 * Outlined Pixel Gamepad Icon (19.dp) matching bottom bar Chats icon weight.
 */
@Composable
fun PixelGameIcon(
    tint: Color = PlaceholderText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(19.dp)) {
        val u = size.width / 10f
        // Outer gamepad frame
        drawRect(tint, Offset(1.5f * u, 2.5f * u), Size(7 * u, 0.9f * u))
        drawRect(tint, Offset(1.5f * u, 7.2f * u), Size(7 * u, 0.9f * u))
        drawRect(tint, Offset(0.8f * u, 3.3f * u), Size(0.9f * u, 4 * u))
        drawRect(tint, Offset(8.3f * u, 3.3f * u), Size(0.9f * u, 4 * u))
        // D-pad left
        drawRect(tint, Offset(2.4f * u, 4.7f * u), Size(2.2f * u, 0.9f * u))
        drawRect(tint, Offset(3.05f * u, 4.0f * u), Size(0.9f * u, 2.2f * u))
        // Action buttons right
        drawRect(tint, Offset(6.6f * u, 4.1f * u), Size(1 * u, 1 * u))
        drawRect(tint, Offset(5.6f * u, 5.2f * u), Size(1 * u, 1 * u))
    }
}

/**
 * Outlined Pixel Person / Me Icon (19.dp) matching bottom bar Chats icon weight.
 */
@Composable
fun PixelPersonIcon(
    tint: Color = PlaceholderText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(19.dp)) {
        val u = size.width / 10f
        // Outlined square-rounded head
        drawRect(tint, Offset(3.5f * u, 1.0f * u), Size(3 * u, 0.9f * u))
        drawRect(tint, Offset(3.5f * u, 4.2f * u), Size(3 * u, 0.9f * u))
        drawRect(tint, Offset(2.9f * u, 1.7f * u), Size(0.9f * u, 2.7f * u))
        drawRect(tint, Offset(6.2f * u, 1.7f * u), Size(0.9f * u, 2.7f * u))
        // Outlined shoulders arch
        drawRect(tint, Offset(2.2f * u, 5.8f * u), Size(5.6f * u, 0.9f * u))
        drawRect(tint, Offset(1.4f * u, 6.6f * u), Size(0.9f * u, 2.2f * u))
        drawRect(tint, Offset(7.7f * u, 6.6f * u), Size(0.9f * u, 2.2f * u))
        drawRect(tint, Offset(1.4f * u, 8.2f * u), Size(7.2f * u, 0.9f * u))
    }
}

/**
 * WhatsApp-style View-Once Pixel Icon: dashed ring + centered "1".
 */
@Composable
fun PixelViewOnceIcon(
    tint: Color = TheirBubbleText,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bloom = isBloomTheme
    Canvas(modifier = modifier.size(18.dp)) {
        val u = size.width / 10f
        if (bloom) {
            val petalColor = if (active) Color(0xFFF482AA) else tint
            val stemColor = if (active) Color(0xFF478A3B) else tint
            // Pink pixel tulip bud
            drawRect(petalColor, Offset(3.5f * u, 1.2f * u), Size(1.1f * u, 1.4f * u))
            drawRect(petalColor, Offset(5.4f * u, 1.2f * u), Size(1.1f * u, 1.4f * u))
            drawRect(petalColor, Offset(3.2f * u, 2.4f * u), Size(3.6f * u, 2.8f * u))
            // Green calyx & stem
            drawRect(stemColor, Offset(3.2f * u, 5.2f * u), Size(3.6f * u, 1.1f * u))
            drawRect(stemColor, Offset(4.4f * u, 6.2f * u), Size(1.2f * u, 2.8f * u))
        } else {
            val ringColor = if (active) LanternAmber else tint
            // Left solid half-arc segments
            drawRect(ringColor, Offset(2.2f * u, 1.0f * u), Size(2.5f * u, 0.9f * u))
            drawRect(ringColor, Offset(1.0f * u, 2.0f * u), Size(1.1f * u, 6.0f * u))
            drawRect(ringColor, Offset(2.2f * u, 8.1f * u), Size(2.5f * u, 0.9f * u))
            // Right dashed segments
            drawRect(ringColor, Offset(5.6f * u, 1.0f * u), Size(2.0f * u, 0.9f * u))
            drawRect(ringColor, Offset(8.0f * u, 2.3f * u), Size(1.0f * u, 1.4f * u))
            drawRect(ringColor, Offset(8.0f * u, 4.4f * u), Size(1.0f * u, 1.4f * u))
            drawRect(ringColor, Offset(8.0f * u, 6.5f * u), Size(1.0f * u, 1.4f * u))
            drawRect(ringColor, Offset(5.6f * u, 8.1f * u), Size(2.0f * u, 0.9f * u))
            // Numeral "1" inside
            drawRect(ringColor, Offset(4.7f * u, 3.0f * u), Size(1.0f * u, 4.0f * u))
            drawRect(ringColor, Offset(3.8f * u, 3.3f * u), Size(1.0f * u, 0.9f * u))
            drawRect(ringColor, Offset(3.8f * u, 6.2f * u), Size(2.6f * u, 0.8f * u))
        }
    }
}

/**
 * Outlined Pixel Video Icon.
 */
@Composable
fun PixelVideoIcon(
    tint: Color = TheirBubbleText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(1f * u, 2.5f * u), Size(5.5f * u, 0.9f * u))
        drawRect(tint, Offset(1f * u, 6.8f * u), Size(5.5f * u, 0.9f * u))
        drawRect(tint, Offset(1f * u, 2.5f * u), Size(0.9f * u, 5.2f * u))
        drawRect(tint, Offset(5.8f * u, 2.5f * u), Size(0.9f * u, 5.2f * u))
        // Triangle lens wing
        drawRect(tint, Offset(7.0f * u, 3.8f * u), Size(1.0f * u, 2.6f * u))
        drawRect(tint, Offset(8.0f * u, 2.9f * u), Size(1.0f * u, 4.4f * u))
    }
}

/**
 * Outlined Pixel Chevron Right (>).
 */
@Composable
fun PixelChevronRightIcon(
    tint: Color = MutedText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(14.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(3f * u, 2f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(4.2f * u, 3.2f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(5.4f * u, 4.4f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(4.2f * u, 5.6f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(3f * u, 6.8f * u), Size(1.2f * u, 1.2f * u))
    }
}

@Composable
fun PixelPaletteIcon(
    tint: Color = LanternAmber,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(2f * u, 1.5f * u), Size(6 * u, 0.9f * u))
        drawRect(tint, Offset(2f * u, 7.6f * u), Size(3.5f * u, 0.9f * u))
        drawRect(tint, Offset(1f * u, 2.4f * u), Size(0.9f * u, 5.2f * u))
        drawRect(tint, Offset(8f * u, 2.4f * u), Size(0.9f * u, 2.8f * u))
        drawRect(tint, Offset(5.5f * u, 5.2f * u), Size(3 * u, 0.9f * u))
        drawRect(tint, Offset(5.5f * u, 5.2f * u), Size(0.9f * u, 2.4f * u))
        // Swatch dots
        drawRect(tint, Offset(3f * u, 3.2f * u), Size(1.1f * u, 1.1f * u))
        drawRect(tint, Offset(5.3f * u, 3.0f * u), Size(1.1f * u, 1.1f * u))
        drawRect(tint, Offset(2.8f * u, 5.2f * u), Size(1.1f * u, 1.1f * u))
    }
}

@Composable
fun PixelShieldIcon(
    tint: Color = SecureGreen,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(2f * u, 1.5f * u), Size(6 * u, 0.9f * u))
        drawRect(tint, Offset(1.5f * u, 2.3f * u), Size(0.9f * u, 3.5f * u))
        drawRect(tint, Offset(7.6f * u, 2.3f * u), Size(0.9f * u, 3.5f * u))
        drawRect(tint, Offset(2.4f * u, 5.8f * u), Size(1.1f * u, 1.1f * u))
        drawRect(tint, Offset(6.5f * u, 5.8f * u), Size(1.1f * u, 1.1f * u))
        drawRect(tint, Offset(3.5f * u, 6.9f * u), Size(1.1f * u, 1.1f * u))
        drawRect(tint, Offset(5.4f * u, 6.9f * u), Size(1.1f * u, 1.1f * u))
        drawRect(tint, Offset(4.4f * u, 7.8f * u), Size(1.2f * u, 1.1f * u))
    }
}

@Composable
fun PixelBellIcon(
    tint: Color = LanternAmber,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(4.5f * u, 1.0f * u), Size(1f * u, 1.0f * u))
        drawRect(tint, Offset(3.0f * u, 2.0f * u), Size(4f * u, 0.9f * u))
        drawRect(tint, Offset(2.2f * u, 2.9f * u), Size(0.9f * u, 3.5f * u))
        drawRect(tint, Offset(6.9f * u, 2.9f * u), Size(0.9f * u, 3.5f * u))
        drawRect(tint, Offset(1.2f * u, 6.4f * u), Size(7.6f * u, 0.9f * u))
        drawRect(tint, Offset(4.0f * u, 7.6f * u), Size(2.0f * u, 1.0f * u))
    }
}

@Composable
fun PixelDatabaseIcon(
    tint: Color = TheirBubbleText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(1.8f * u, 1.4f * u), Size(6.4f * u, 0.9f * u))
        drawRect(tint, Offset(1.8f * u, 4.0f * u), Size(6.4f * u, 0.9f * u))
        drawRect(tint, Offset(1.8f * u, 6.2f * u), Size(6.4f * u, 0.9f * u))
        drawRect(tint, Offset(1.8f * u, 8.2f * u), Size(6.4f * u, 0.9f * u))
        drawRect(tint, Offset(1.5f * u, 1.4f * u), Size(0.9f * u, 7.7f * u))
        drawRect(tint, Offset(7.6f * u, 1.4f * u), Size(0.9f * u, 7.7f * u))
    }
}

@Composable
fun PixelUsersIcon(
    tint: Color = Color(0xFF70D6FF),
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val u = size.width / 10f
        // Primary head & shoulders
        drawRect(tint, Offset(2.2f * u, 1.6f * u), Size(2.6f * u, 2.6f * u))
        drawRect(tint, Offset(1.0f * u, 5.2f * u), Size(5.0f * u, 0.9f * u))
        drawRect(tint, Offset(1.0f * u, 6.0f * u), Size(0.9f * u, 2.3f * u))
        drawRect(tint, Offset(5.1f * u, 6.0f * u), Size(0.9f * u, 2.3f * u))
        // Secondary peer offset
        drawRect(tint, Offset(5.6f * u, 2.4f * u), Size(2.2f * u, 2.2f * u))
        drawRect(tint, Offset(6.2f * u, 5.5f * u), Size(2.8f * u, 0.9f * u))
        drawRect(tint, Offset(8.1f * u, 6.3f * u), Size(0.9f * u, 2.0f * u))
    }
}

@Composable
fun PixelStarIcon(
    tint: Color = LanternAmber,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        if (filled) {
            drawRect(tint, Offset(4.4f * u, 1.0f * u), Size(1.2f * u, 8.0f * u))
            drawRect(tint, Offset(3.4f * u, 2.5f * u), Size(3.2f * u, 5.0f * u))
            drawRect(tint, Offset(1.0f * u, 3.6f * u), Size(8.0f * u, 1.6f * u))
            drawRect(tint, Offset(2.0f * u, 5.0f * u), Size(6.0f * u, 1.5f * u))
            drawRect(tint, Offset(2.2f * u, 6.5f * u), Size(1.8f * u, 2.0f * u))
            drawRect(tint, Offset(6.0f * u, 6.5f * u), Size(1.8f * u, 2.0f * u))
        } else {
            drawRect(tint, Offset(4.4f * u, 1.0f * u), Size(1.2f * u, 1.5f * u))
            drawRect(tint, Offset(3.4f * u, 2.5f * u), Size(1.0f * u, 1.2f * u))
            drawRect(tint, Offset(5.6f * u, 2.5f * u), Size(1.0f * u, 1.2f * u))
            drawRect(tint, Offset(1.0f * u, 3.7f * u), Size(2.6f * u, 1.0f * u))
            drawRect(tint, Offset(6.4f * u, 3.7f * u), Size(2.6f * u, 1.0f * u))
            drawRect(tint, Offset(2.0f * u, 4.7f * u), Size(1.2f * u, 1.2f * u))
            drawRect(tint, Offset(6.8f * u, 4.7f * u), Size(1.2f * u, 1.2f * u))
            drawRect(tint, Offset(2.2f * u, 5.9f * u), Size(1.1f * u, 2.6f * u))
            drawRect(tint, Offset(6.7f * u, 5.9f * u), Size(1.1f * u, 2.6f * u))
            drawRect(tint, Offset(3.8f * u, 7.0f * u), Size(2.4f * u, 1.0f * u))
        }
    }
}

@Composable
fun PixelMuteBellIcon(
    tint: Color = MutedText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        drawRect(tint, Offset(4.5f * u, 1.0f * u), Size(1.0f * u, 1.0f * u))
        drawRect(tint, Offset(3.0f * u, 2.0f * u), Size(4.0f * u, 0.9f * u))
        drawRect(tint, Offset(2.2f * u, 2.9f * u), Size(0.9f * u, 3.5f * u))
        drawRect(tint, Offset(6.9f * u, 2.9f * u), Size(0.9f * u, 3.5f * u))
        drawRect(tint, Offset(1.2f * u, 6.4f * u), Size(7.6f * u, 0.9f * u))
        drawRect(tint, Offset(4.0f * u, 7.6f * u), Size(2.0f * u, 1.0f * u))
        for (i in 1..8) {
            drawRect(tint, Offset(i * u, i * u), Size(1.0f * u, 1.0f * u))
        }
    }
}


/**
 * Scattered Pixel Stars (2-3px squares).
 */
@Composable
fun PixelStarfield(
    modifier: Modifier = Modifier,
) {
    val bloom = isBloomTheme
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val primaryColor = if (bloom) Color(0xFFF6C5D6) else StarYellow
        val secondaryColor = if (bloom) Color(0xFFCAE5F5) else MutedText

        val starPositions = listOf(
            Offset(0.15f * w, 0.08f * h) to primaryColor,
            Offset(0.70f * w, 0.07f * h) to secondaryColor,
            Offset(0.85f * w, 0.12f * h) to primaryColor,
            Offset(0.08f * w, 0.22f * h) to secondaryColor,
            Offset(0.78f * w, 0.23f * h) to secondaryColor,
            Offset(0.28f * w, 0.35f * h) to primaryColor,
            Offset(0.75f * w, 0.42f * h) to primaryColor,
            Offset(0.18f * w, 0.54f * h) to secondaryColor,
            Offset(0.86f * w, 0.45f * h) to secondaryColor,
            Offset(0.29f * w, 0.57f * h) to primaryColor,
            Offset(0.63f * w, 0.64f * h) to primaryColor,
            Offset(0.88f * w, 0.63f * h) to primaryColor,
            Offset(0.77f * w, 0.74f * h) to secondaryColor,
            Offset(0.22f * w, 0.88f * h) to primaryColor,
            Offset(0.82f * w, 0.90f * h) to secondaryColor,
        )

        for ((pos, color) in starPositions) {
            drawRect(color, pos, Size(3.dp.toPx(), 3.dp.toPx()))
        }
    }
}

/**
 * Row of 10 burning ember squares (or pink petals in Bloom Theme) for disappearing messages.
 */
@Composable
fun DisappearingEmbers(
    total: Int = 10,
    litCount: Int,
    modifier: Modifier = Modifier,
) {
    val litColor = if (isBloomTheme) MyBubbleFill else LanternAmber
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
                    .background(if (isLit) litColor else MothEmberUnlit),
            )
        }
    }
}

/**
 * Outlined Pixel Save / Download Icon (arrow into tray).
 */
@Composable
fun PixelSaveIcon(
    tint: Color = LanternAmber,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        // Down arrow shaft & head
        drawRect(tint, Offset(4.4f * u, 1.0f * u), Size(1.2f * u, 5.2f * u))
        drawRect(tint, Offset(2.8f * u, 4.0f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(6.0f * u, 4.0f * u), Size(1.2f * u, 1.2f * u))
        drawRect(tint, Offset(3.8f * u, 5.0f * u), Size(2.4f * u, 1.2f * u))
        // Bottom tray
        drawRect(tint, Offset(1.2f * u, 6.6f * u), Size(1.0f * u, 2.2f * u))
        drawRect(tint, Offset(7.8f * u, 6.6f * u), Size(1.0f * u, 2.2f * u))
        drawRect(tint, Offset(1.2f * u, 7.9f * u), Size(7.6f * u, 1.0f * u))
    }
}

/**
 * Outlined Pixel Copy / Clipboard Icon (two layered sheets).
 */
@Composable
fun PixelCopyIcon(
    tint: Color = TheirBubbleText,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val u = size.width / 10f
        // Back sheet (top-left L)
        drawRect(tint, Offset(1.2f * u, 1.2f * u), Size(5.0f * u, 1.0f * u))
        drawRect(tint, Offset(1.2f * u, 1.2f * u), Size(1.0f * u, 5.5f * u))
        // Front sheet
        drawRect(tint, Offset(3.5f * u, 3.3f * u), Size(5.2f * u, 1.0f * u))
        drawRect(tint, Offset(3.5f * u, 8.0f * u), Size(5.2f * u, 1.0f * u))
        drawRect(tint, Offset(3.5f * u, 3.3f * u), Size(1.0f * u, 5.7f * u))
        drawRect(tint, Offset(7.7f * u, 3.3f * u), Size(1.0f * u, 5.7f * u))
    }
}


