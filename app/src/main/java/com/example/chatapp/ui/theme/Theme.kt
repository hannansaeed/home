package com.example.chatapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.R

// Bundled Google Font: Pixelify Sans
val PixelifySans = FontFamily(Font(R.font.pixelify_sans, FontWeight.Normal))

// "Moth hours" Palette (Exact match to specification & screenshots)
val MothBgDark = Color(0xFF1A1633)          // Main background
val MothHeaderBg = Color(0xFF231E4A)        // Header & bottom input bar
val MothDivider = Color(0xFF3A3270)         // Frame, lines & dividers

// Message Bubbles
val TheirBubbleFill = Color(0xFF2F2A5E)     // Their bubble fill
val TheirBubbleBorder = Color(0xFF5B52A8)   // Their bubble border
val TheirBubbleText = Color(0xFFF4E4C1)     // Their bubble text (warm cream)

val MyBubbleFill = Color(0xFFFFBF4D)        // My bubble fill (amber)
val MyBubbleBorder = Color(0xFFC9791A)      // My bubble border (dark amber)
val MyBubbleText = Color(0xFF3A2208)        // My bubble text (dark brown)

// Accents
val LanternAmber = Color(0xFFFFB43B)        // Lantern accent & lit color
val LanternGlow = Color(0xFFFFF2D1)         // Lantern core glow
val LanternDim = Color(0xFF433D6B)          // Offline/dim lantern
val WaxSealRed = Color(0xFFD8452F)          // Wax seal red
val WaxSealDarkEdge = Color(0xFFA52F1F)     // Wax seal dark edge
val MutedText = Color(0xFF9A93D6)           // Muted text & secondary labels
val PlaceholderText = Color(0xFF7D75C4)     // Placeholder text
val SecureGreen = Color(0xFF7FD6A8)         // "sealed" & security green
val StarYellow = Color(0xFFFFFAE0)          // Star pixel color

// Stepped / Notched Corner Shape (3px-4px notch at each corner, flat pixel art aesthetic)
class SteppedCornerShape(val cutSize: Dp = 4.dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cut = with(density) { cutSize.toPx() }
        val path = Path().apply {
            moveTo(cut, 0f)
            lineTo(size.width - cut, 0f)
            lineTo(size.width, cut)
            lineTo(size.width, size.height - cut)
            lineTo(size.width - cut, size.height)
            lineTo(cut, size.height)
            lineTo(0f, size.height - cut)
            lineTo(0f, cut)
            close()
        }
        return Outline.Generic(path)
    }
}

val MothTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PixelifySans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        color = TheirBubbleText,
    ),
    headlineMedium = TextStyle(
        fontFamily = PixelifySans,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = LanternAmber,
    ),
    titleLarge = TextStyle(
        fontFamily = PixelifySans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = LanternAmber,
    ),
    titleMedium = TextStyle(
        fontFamily = PixelifySans,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TheirBubbleText,
    ),
    bodyLarge = TextStyle(
        fontFamily = PixelifySans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = TheirBubbleText,
    ),
    bodyMedium = TextStyle(
        fontFamily = PixelifySans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = TheirBubbleText,
    ),
    bodySmall = TextStyle(
        fontFamily = PixelifySans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = MutedText,
    ),
    labelLarge = TextStyle(
        fontFamily = PixelifySans,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = TheirBubbleText,
    ),
    labelMedium = TextStyle(
        fontFamily = PixelifySans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = MutedText,
    ),
    labelSmall = TextStyle(
        fontFamily = PixelifySans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = MutedText,
    ),
)

private val MothColorScheme = darkColorScheme(
    primary = LanternAmber,
    onPrimary = MyBubbleText,
    primaryContainer = MothHeaderBg,
    onPrimaryContainer = LanternAmber,
    secondary = WaxSealRed,
    onSecondary = Color.White,
    background = MothBgDark,
    onBackground = TheirBubbleText,
    surface = MothHeaderBg,
    onSurface = TheirBubbleText,
    surfaceVariant = TheirBubbleFill,
    onSurfaceVariant = MutedText,
    outline = MothDivider,
    error = WaxSealRed,
)

@Composable
fun ChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MothColorScheme,
        typography = MothTypography,
        content = content,
    )
}

/**
 * Stepped-Corner Box Container: flat pixel aesthetic with 3px/2.dp border and notched corners.
 */
@Composable
fun SteppedBox(
    modifier: Modifier = Modifier,
    backgroundColor: Color = TheirBubbleFill,
    borderColor: Color = TheirBubbleBorder,
    borderWidth: Dp = 2.dp,
    cutSize: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = SteppedCornerShape(cutSize)
    Box(
        modifier = modifier
            .background(backgroundColor, shape)
            .border(borderWidth, borderColor, shape),
        content = content,
    )
}

/**
 * Stepped-Corner Button (flat, no gradients, no shadows, no curves).
 */
@Composable
fun SteppedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MyBubbleFill,
    borderColor: Color = MyBubbleBorder,
    textColor: Color = MyBubbleText,
    cutSize: Dp = 4.dp,
    borderWidth: Dp = 2.dp,
    enabled: Boolean = true,
) {
    val shape = SteppedCornerShape(cutSize)
    Box(
        modifier = modifier
            .background(backgroundColor, shape)
            .border(borderWidth, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = PixelifySans,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}
