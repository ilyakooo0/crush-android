package soy.iko.crush.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Charm-style pink/purple accent on a deep navy background.
private val CrushPink = Color(0xFFFF6B9D)
private val CrushPurple = Color(0xFFB57EDC)
private val CrushNavy = Color(0xFF0F0F1A)
private val CrushNavyLight = Color(0xFF1A1A2E)
private val CrushSurface = Color(0xFF1E1E2E)
private val CrushSurfaceLight = Color(0xFFF5F5F7)

private val DarkColors = darkColorScheme(
    primary = CrushPink,
    onPrimary = Color.White,
    secondary = CrushPurple,
    onSecondary = Color.White,
    background = CrushNavy,
    onBackground = Color(0xFFE0E0E8),
    surface = CrushSurface,
    onSurface = Color(0xFFE0E0E8),
    surfaceVariant = CrushNavyLight,
    onSurfaceVariant = Color(0xFFB0B0C0),
)

private val LightColors = lightColorScheme(
    primary = CrushPink,
    onPrimary = Color.White,
    secondary = CrushPurple,
    onSecondary = Color.White,
    background = CrushSurfaceLight,
    onBackground = Color(0xFF1A1A2E),
    surface = Color.White,
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFE8E8EE),
    onSurfaceVariant = Color(0xFF555566),
)

@Composable
fun CrushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
