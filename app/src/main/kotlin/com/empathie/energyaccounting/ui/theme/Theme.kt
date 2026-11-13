package com.empathie.energyaccounting.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme preference stored by the Manage & Export tab.
 */
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

/**
 * Accents and surface tones the Material scheme does not carry.
 */
data class EmpathieColors(
    val gain: Color,
    val spend: Color,
    val net: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val border: Color,
    val onSurfaceMuted: Color,
    val dimensionColors: List<Color>,
    val isDark: Boolean,
)

val LocalEmpathieColors = staticCompositionLocalOf {
    EmpathieColors(
        gain = GainSageDark,
        spend = SpendAmberDark,
        net = NetPeriwinkleDark,
        surfaceRaised = SurfaceRaisedDark,
        surfaceSunken = SurfaceSunkenDark,
        border = BorderDark,
        onSurfaceMuted = OnSurfaceMutedDark,
        dimensionColors = DimensionColorsDark,
        isDark = true,
    )
}

private val DarkColors = darkColorScheme(
    primary = NetPeriwinkleDark,
    onPrimary = Color(0xFF10203A),
    primaryContainer = Color(0xFF2E3B56),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = GainSageDark,
    onSecondary = Color(0xFF052B22),
    tertiary = SpendAmberDark,
    onTertiary = Color(0xFF3A1E05),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceRaisedDark,
    onSurfaceVariant = OnSurfaceMutedDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = SpendAmberDark,
    onError = Color(0xFF2B1405),
)

private val WarmLightColors = lightColorScheme(
    primary = NetPeriwinkleLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F7),
    onPrimaryContainer = Color(0xFF1B2B47),
    secondary = GainSageLight,
    onSecondary = Color.White,
    tertiary = SpendAmberLight,
    onTertiary = Color.White,
    background = BackgroundWarmLight,
    onBackground = OnBackgroundWarmLight,
    surface = SurfaceWarmLight,
    onSurface = OnBackgroundWarmLight,
    surfaceVariant = SurfaceRaisedWarmLight,
    onSurfaceVariant = OnSurfaceMutedWarmLight,
    outline = BorderWarmLight,
    outlineVariant = BorderWarmLight,
    error = SpendAmberLight,
    onError = Color.White,
)

@Composable
fun EmpathieTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val empathieColors = if (isDark) {
        EmpathieColors(
            gain = GainSageDark,
            spend = SpendAmberDark,
            net = NetPeriwinkleDark,
            surfaceRaised = SurfaceRaisedDark,
            surfaceSunken = SurfaceSunkenDark,
            border = BorderDark,
            onSurfaceMuted = OnSurfaceMutedDark,
            dimensionColors = DimensionColorsDark,
            isDark = true,
        )
    } else {
        EmpathieColors(
            gain = GainSageLight,
            spend = SpendAmberLight,
            net = NetPeriwinkleLight,
            surfaceRaised = SurfaceRaisedWarmLight,
            surfaceSunken = SurfaceSunkenWarmLight,
            border = BorderWarmLight,
            onSurfaceMuted = OnSurfaceMutedWarmLight,
            dimensionColors = DimensionColorsLight,
            isDark = false,
        )
    }

    CompositionLocalProvider(LocalEmpathieColors provides empathieColors) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColors else WarmLightColors,
            content = content,
        )
    }
}
