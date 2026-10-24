package nl.openschatkamer.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nl.openschatkamer.brand.BrandColors
import nl.openschatkamer.brand.BrandFonts

private val Colors = darkColorScheme(
    primary = BrandColors.Amber,
    onPrimary = BrandColors.Ink,
    primaryContainer = BrandColors.AmberDeep,
    onPrimaryContainer = BrandColors.Ink,
    secondary = BrandColors.Audio,
    onSecondary = BrandColors.Ink,
    background = BrandColors.Ink,
    onBackground = BrandColors.Text,
    surface = BrandColors.Ink,
    onSurface = BrandColors.Text,
    surfaceVariant = BrandColors.Surface,
    onSurfaceVariant = BrandColors.TextMuted,
    surfaceContainer = BrandColors.Surface,
    surfaceContainerHigh = BrandColors.SurfaceHigh,
    surfaceContainerHighest = BrandColors.SurfaceHigh,
    surfaceContainerLow = BrandColors.Surface,
    outline = BrandColors.Outline,
    outlineVariant = BrandColors.Outline,
    error = BrandColors.Danger
)

private val Display = BrandFonts.Display

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontSize = 34.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontSize = 30.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontSize = 22.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = Display, fontSize = 21.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
)

@Composable
fun OpenSchatkamerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
