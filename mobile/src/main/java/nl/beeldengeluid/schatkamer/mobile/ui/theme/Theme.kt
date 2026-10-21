package nl.beeldengeluid.schatkamer.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SchatkamerCyan,
    onPrimary = Color.Black,
    secondary = SchatkamerMagenta,
    onSecondary = Color.White,
    tertiary = SchatkamerCyan,
    background = SchatkamerDarkBackground,
    onBackground = SchatkamerTextPrimary,
    surface = SchatkamerDarkSurface,
    onSurface = SchatkamerTextPrimary,
    surfaceVariant = SchatkamerDarkElevated,
    onSurfaceVariant = SchatkamerTextSecondary,
    outline = SchatkamerBorder
)

@Composable
fun SchatkamerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
