package nl.beeldengeluid.schatkamer.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
private val TvDarkColorScheme = darkColorScheme(
    primary = SchatkamerCyan,
    onPrimary = Color.Black,
    secondary = SchatkamerMagenta,
    onSecondary = Color.White,
    background = SchatkamerDarkBackground,
    onBackground = SchatkamerTextPrimary,
    surface = SchatkamerDarkSurface,
    onSurface = SchatkamerTextPrimary,
    surfaceVariant = SchatkamerDarkElevated,
    onSurfaceVariant = SchatkamerTextSecondary,
    border = SchatkamerBorder
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SchatkamerTvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        content = content
    )
}
