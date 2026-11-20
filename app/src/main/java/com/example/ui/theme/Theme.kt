package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.datastore.AppThemeMode

@Immutable
data class NothingColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val borderSubtle: Color,
    val accentPrimary: Color,
    val onAccentPrimary: Color,
    val dotMatrixColor: Color,
    val red: Color,
    val amber: Color,
    val green: Color,
    val capsuleNavBg: Color,
    val capsuleNavBorder: Color,
    val capsuleNavActivePill: Color,
    val capsuleNavActiveContent: Color,
    val capsuleNavInactiveContent: Color
)

private val DarkNothingColors = NothingColors(
    isDark = true,
    background = NothingDarkBg,
    surface = NothingDarkSurface,
    surfaceElevated = NothingDarkSurfaceElevated,
    textPrimary = NothingDarkText,
    textSecondary = NothingDarkSecondaryText,
    border = NothingDarkBorder,
    borderSubtle = NothingDarkBorderSubtle,
    accentPrimary = NothingDarkText,
    onAccentPrimary = NothingDarkBg,
    dotMatrixColor = Color(0xFF2D2D34),
    red = NothingRed,
    amber = NothingAmber,
    green = NothingGreen,
    capsuleNavBg = Color(0xF5141417),
    capsuleNavBorder = Color(0xFF2E2E35),
    capsuleNavActivePill = NothingDarkText,
    capsuleNavActiveContent = NothingDarkBg,
    capsuleNavInactiveContent = NothingDarkSecondaryText
)

private val LightNothingColors = NothingColors(
    isDark = false,
    background = NothingLightBg,
    surface = NothingLightSurface,
    surfaceElevated = NothingLightSurfaceElevated,
    textPrimary = NothingLightText,
    textSecondary = NothingLightSecondaryText,
    border = NothingLightBorder,
    borderSubtle = NothingLightBorderSubtle,
    accentPrimary = NothingLightText,
    onAccentPrimary = Color.White,
    dotMatrixColor = Color(0xFFD0D0D6),
    red = NothingRed,
    amber = NothingAmber,
    green = NothingGreen,
    capsuleNavBg = Color(0xF5FFFFFF),
    capsuleNavBorder = Color(0xFFD2D2D8),
    capsuleNavActivePill = NothingLightText,
    capsuleNavActiveContent = Color.White,
    capsuleNavInactiveContent = NothingLightSecondaryText
)

val LocalNothingColors = staticCompositionLocalOf { DarkNothingColors }

object NothingTheme {
    val colors: NothingColors
        @Composable get() = LocalNothingColors.current
}

private val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = NothingDarkBorderSubtle,
    error = NothingRed,
    onError = Color.White,
    errorContainer = NothingRed.copy(alpha = 0.16f),
    onErrorContainer = NothingRed
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = NothingLightBorderSubtle,
    error = NothingRed,
    onError = Color.White,
    errorContainer = NothingRed.copy(alpha = 0.12f),
    onErrorContainer = NothingRed
)

fun resolveIsDarkTheme(mode: AppThemeMode, systemDark: Boolean): Boolean = when (mode) {
    AppThemeMode.SYSTEM -> systemDark
    AppThemeMode.DARK -> true
    AppThemeMode.LIGHT -> false
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val nothingColors = if (darkTheme) DarkNothingColors else LightNothingColors

    CompositionLocalProvider(LocalNothingColors provides nothingColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = NothingShapes,
            content = content
        )
    }
}
