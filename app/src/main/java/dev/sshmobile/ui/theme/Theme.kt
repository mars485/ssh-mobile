package dev.sshmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.sshmobile.data.repository.ThemeMode

private val DarkColors = darkColorScheme(
    primary = GreenAccent,
    onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF0B3D24),
    onPrimaryContainer = GreenSoft,
    secondary = GreenSoft,
    onSecondary = Color(0xFF00210F),
    secondaryContainer = Color(0xFF16301F),
    onSecondaryContainer = GreenSoft,
    tertiary = Color(0xFF6FD3FF),
    background = TerminalBlack,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = Color(0xFF243028),
    outline = OutlineDark,
    outlineVariant = Color(0xFF1E2A23),
    error = ErrorRed,
    onError = Color(0xFF2A0000),
    errorContainer = Color(0xFF3B1212),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = GreenDeepLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F2D3),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = GreenDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F3E2),
    onSecondaryContainer = Color(0xFF00210F),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceVariantLight,
    surfaceContainerHighest = Color(0xFFD5E6DB),
    outline = OutlineLight,
    outlineVariant = Color(0xFFDCEAE1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

val TerminalFontFamily = FontFamily.Monospace

private val AppTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
    )
}

private val TerminalTypography = TextStyle(
    fontFamily = TerminalFontFamily,
    fontSize = 14.sp,
    lineHeight = 18.sp,
)

@Composable
fun SshMobileTheme(
    themeMode: ThemeMode = if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode == ThemeMode.DARK) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

val TerminalTextStyle: TextStyle
    get() = TerminalTypography
