package by.iseu.schedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import by.iseu.schedule.data.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A0A0A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF0A0A0A),
    secondary = Color(0xFF6B6B6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5F5F5),
    onSecondaryContainer = Color(0xFF0A0A0A),
    tertiary = Color(0xFF6B6B6B),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0A0A0A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF6B6B6B),
    outline = Color(0xFFD4D4D4),
    outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFF0A0A0A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFE5E5E5),
    onErrorContainer = Color(0xFF0A0A0A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = Color(0xFF0A0A0A),
    primaryContainer = Color(0xFF2E2E2E),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFA3A3A3),
    onSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Color(0xFF161616),
    onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFFA3A3A3),
    onTertiary = Color(0xFF0A0A0A),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF2E2E2E),
    outlineVariant = Color(0xFF1F1F1F),
    error = Color(0xFFF5F5F5),
    onError = Color(0xFF0A0A0A),
    errorContainer = Color(0xFF2E2E2E),
    onErrorContainer = Color(0xFFF5F5F5),
)

@Composable
fun IseuTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
