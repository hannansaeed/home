package com.example.chatapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

// Bundled Google Fonts (Pixel / Retro + Clean Sans)
val DefaultPixelifySans = FontFamily(Font(R.font.pixelify_sans, FontWeight.Normal))
val SilkscreenFont = FontFamily(Font(R.font.silkscreen, FontWeight.Normal))
val Vt323Font = FontFamily(Font(R.font.vt323, FontWeight.Normal))
val DotGothicFont = FontFamily(Font(R.font.dotgothic16, FontWeight.Normal))
val Jersey10Font = FontFamily(Font(R.font.jersey_10, FontWeight.Normal))
val Jersey15Font = FontFamily(Font(R.font.jersey_15, FontWeight.Normal))
val Jersey20Font = FontFamily(Font(R.font.jersey_20, FontWeight.Normal))
val Tiny5Font = FontFamily(Font(R.font.tiny5, FontWeight.Normal))
val JetBrainsMonoFont = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))
val SpaceMonoFont = FontFamily(Font(R.font.space_mono, FontWeight.Normal))

val InterFont = FontFamily(Font(R.font.inter, FontWeight.Normal))
val PlusJakartaFont = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
val OpenSansFont = FontFamily(Font(R.font.open_sans, FontWeight.Normal))
val PoppinsFont = FontFamily(Font(R.font.poppins, FontWeight.Normal))
val NunitoFont = FontFamily(Font(R.font.nunito, FontWeight.Normal))
val DmSansFont = FontFamily(Font(R.font.dm_sans, FontWeight.Normal))
val RobotoFont = FontFamily(Font(R.font.roboto, FontWeight.Normal))

enum class AppFontOption(
    val id: String,
    val label: String,
    val isPixelStyle: Boolean,
    val fontFamily: FontFamily,
) {
    PIXELIFY_SANS("pixelify_sans", "pixelify sans", true, DefaultPixelifySans),
    SILKSCREEN("silkscreen", "silkscreen", true, SilkscreenFont),
    VT323("vt323", "vt323", true, Vt323Font),
    DOTGOTHIC("dotgothic16", "dotgothic", true, DotGothicFont),
    JERSEY_10("jersey_10", "jersey 10", true, Jersey10Font),
    JERSEY_15("jersey_15", "jersey 15", true, Jersey15Font),
    JERSEY_20("jersey_20", "jersey 20", true, Jersey20Font),
    TINY5("tiny5", "tiny5", true, Tiny5Font),
    JETBRAINS_MONO("jetbrains_mono", "jetbrains mono", true, JetBrainsMonoFont),
    SPACE_MONO("space_mono", "space mono", true, SpaceMonoFont),
    INTER("inter", "inter", false, InterFont),
    PLUS_JAKARTA_SANS("plus_jakarta_sans", "jakarta sans", false, PlusJakartaFont),
    OPEN_SANS("open_sans", "open sans", false, OpenSansFont),
    POPPINS("poppins", "poppins", false, PoppinsFont),
    NUNITO("nunito", "nunito", false, NunitoFont),
    DM_SANS("dm_sans", "dm sans", false, DmSansFont),
    ROBOTO("roboto", "roboto", false, RobotoFont);

    companion object {
        fun fromId(id: String?): AppFontOption {
            return entries.firstOrNull { it.id == id } ?: PIXELIFY_SANS
        }
    }
}

enum class AppThemeOption(
    val id: String,
    val label: String,
    val subtitle: String,
) {
    LANTERN_NIGHT(
        id = "lantern_night",
        label = "lighting lanterns",
        subtitle = "indigo night • amber glow",
    ),
    FLOWER_BLOOM(
        id = "flower_bloom",
        label = "flowers blooming",
        subtitle = "warm cream • sage & petals",
    );

    companion object {
        fun fromId(id: String?): AppThemeOption {
            return entries.firstOrNull { it.id == id } ?: LANTERN_NIGHT
        }
    }
}

data class MothPalette(
    val bgDark: Color,
    val headerBg: Color,
    val divider: Color,
    val inputBg: Color,
    val theirBubbleFill: Color,
    val theirBubbleBorder: Color,
    val theirBubbleText: Color,
    val myBubbleFill: Color,
    val myBubbleBorder: Color,
    val myBubbleText: Color,
    val lanternAmber: Color,
    val lanternGlow: Color,
    val lanternDim: Color,
    val waxSealRed: Color,
    val waxSealDarkEdge: Color,
    val mutedText: Color,
    val placeholderText: Color,
    val secureGreen: Color,
    val starYellow: Color,
    val emberUnlit: Color,
    val scrimSoft: Color,
    val scrimStrong: Color,
    val isLight: Boolean,
)

val LanternNightPalette = MothPalette(
    bgDark = Color(0xFF1A1633),
    headerBg = Color(0xFF231E4A),
    divider = Color(0xFF3A3270),
    inputBg = Color(0xFF1A1633),
    theirBubbleFill = Color(0xFF2F2A5E),
    theirBubbleBorder = Color(0xFF5B52A8),
    theirBubbleText = Color(0xFFF4E4C1),
    myBubbleFill = Color(0xFFFFBF4D),
    myBubbleBorder = Color(0xFFC9791A),
    myBubbleText = Color(0xFF3A2208),
    lanternAmber = Color(0xFFFFB43B),
    lanternGlow = Color(0xFFFFF2D1),
    lanternDim = Color(0xFF433D6B),
    waxSealRed = Color(0xFFD8452F),
    waxSealDarkEdge = Color(0xFFA52F1F),
    mutedText = Color(0xFF9A93D6),
    placeholderText = Color(0xFF7D75C4),
    secureGreen = Color(0xFF7FD6A8),
    starYellow = Color(0xFFFFFAE0),
    emberUnlit = Color(0xFF3A3270),
    scrimSoft = Color(0x991A1633),
    scrimStrong = Color(0xCC1A1633),
    isLight = false,
)

val FlowerBloomPalette = MothPalette(
    bgDark = Color(0xFFFAF5E8),
    headerBg = Color(0xFFE3F0CD),
    divider = Color(0xFF5C8D47),
    inputBg = Color(0xFFFFFDF7),
    theirBubbleFill = Color(0xFFD6ECC0),
    theirBubbleBorder = Color(0xFF72AC54),
    theirBubbleText = Color(0xFF284220),
    myBubbleFill = Color(0xFFF482AA),
    myBubbleBorder = Color(0xFFB83E6F),
    myBubbleText = Color(0xFF2D111E),
    lanternAmber = Color(0xFF2D5624),
    lanternGlow = Color(0xFFFFD147),
    lanternDim = Color(0xFF9EB58F),
    waxSealRed = Color(0xFFD64568),
    waxSealDarkEdge = Color(0xFFA4294A),
    mutedText = Color(0xFF5E7E52),
    placeholderText = Color(0xFF839E76),
    secureGreen = Color(0xFF478A3B),
    starYellow = Color(0xFFF6C5D6),
    emberUnlit = Color(0xFFE5DEC9),
    scrimSoft = Color(0x88243E1C),
    scrimStrong = Color(0xB8243E1C),
    isLight = true,
)

// Active app font family & theme (defaults to Pixelify Sans + Lighting Lanterns; triggers recomposition)
var PixelifySans: FontFamily by mutableStateOf(DefaultPixelifySans)
var activeAppTheme: AppThemeOption by mutableStateOf(AppThemeOption.LANTERN_NIGHT)
val isBloomTheme: Boolean get() = activeAppTheme == AppThemeOption.FLOWER_BLOOM
val activePalette: MothPalette
    get() = if (isBloomTheme) FlowerBloomPalette else LanternNightPalette

// Reactive Palette Tokens
val MothBgDark: Color get() = activePalette.bgDark
val MothHeaderBg: Color get() = activePalette.headerBg
val MothDivider: Color get() = activePalette.divider
val MothInputBg: Color get() = activePalette.inputBg

val TheirBubbleFill: Color get() = activePalette.theirBubbleFill
val TheirBubbleBorder: Color get() = activePalette.theirBubbleBorder
val TheirBubbleText: Color get() = activePalette.theirBubbleText

val MyBubbleFill: Color get() = activePalette.myBubbleFill
val MyBubbleBorder: Color get() = activePalette.myBubbleBorder
val MyBubbleText: Color get() = activePalette.myBubbleText

val LanternAmber: Color get() = activePalette.lanternAmber
val LanternGlow: Color get() = activePalette.lanternGlow
val LanternDim: Color get() = activePalette.lanternDim
val WaxSealRed: Color get() = activePalette.waxSealRed
val WaxSealDarkEdge: Color get() = activePalette.waxSealDarkEdge
val MutedText: Color get() = activePalette.mutedText
val PlaceholderText: Color get() = activePalette.placeholderText
val SecureGreen: Color get() = activePalette.secureGreen
val StarYellow: Color get() = activePalette.starYellow
val MothEmberUnlit: Color get() = activePalette.emberUnlit
val MothScrimSoft: Color get() = activePalette.scrimSoft
val MothScrimStrong: Color get() = activePalette.scrimStrong
val MothInkDark: Color = Color(0xFF1A1633)

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

fun buildMothTypography(fontFamily: FontFamily, palette: MothPalette = activePalette): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        color = palette.theirBubbleText,
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = palette.lanternAmber,
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = palette.lanternAmber,
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = palette.theirBubbleText,
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = palette.theirBubbleText,
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = palette.theirBubbleText,
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = palette.mutedText,
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = palette.theirBubbleText,
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = palette.mutedText,
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = palette.mutedText,
    ),
)

private fun buildColorScheme(palette: MothPalette): ColorScheme =
    if (palette.isLight) {
        lightColorScheme(
            primary = palette.lanternAmber,
            onPrimary = Color.White,
            primaryContainer = palette.headerBg,
            onPrimaryContainer = palette.lanternAmber,
            secondary = palette.myBubbleFill,
            onSecondary = palette.myBubbleText,
            background = palette.bgDark,
            onBackground = palette.theirBubbleText,
            surface = palette.headerBg,
            onSurface = palette.theirBubbleText,
            surfaceVariant = palette.theirBubbleFill,
            onSurfaceVariant = palette.mutedText,
            outline = palette.divider,
            error = palette.waxSealRed,
        )
    } else {
        darkColorScheme(
            primary = palette.lanternAmber,
            onPrimary = palette.myBubbleText,
            primaryContainer = palette.headerBg,
            onPrimaryContainer = palette.lanternAmber,
            secondary = palette.waxSealRed,
            onSecondary = Color.White,
            background = palette.bgDark,
            onBackground = palette.theirBubbleText,
            surface = palette.headerBg,
            onSurface = palette.theirBubbleText,
            surfaceVariant = palette.theirBubbleFill,
            onSurfaceVariant = palette.mutedText,
            outline = palette.divider,
            error = palette.waxSealRed,
        )
    }

@Composable
fun ChatTheme(
    appFontFamily: FontFamily = PixelifySans,
    themeOption: AppThemeOption = activeAppTheme,
    content: @Composable () -> Unit,
) {
    val palette = if (themeOption == AppThemeOption.FLOWER_BLOOM) FlowerBloomPalette else LanternNightPalette
    val colorScheme = remember(palette) { buildColorScheme(palette) }
    val typography = remember(appFontFamily, palette) { buildMothTypography(appFontFamily, palette) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
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
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val shape = SteppedCornerShape(cutSize)
    Box(
        modifier = modifier
            .background(backgroundColor, shape)
            .border(borderWidth, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (text.isBlank()) 10.dp else 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (leadingIcon != null && text.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                leadingIcon()
                Text(
                    text = text,
                    fontFamily = PixelifySans,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
            }
        } else if (leadingIcon != null) {
            leadingIcon()
        } else {
            Text(
                text = text,
                fontFamily = PixelifySans,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
        }
    }
}
