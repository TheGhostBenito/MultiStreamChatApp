package com.example.chatmultistream.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Indica si el usuario activó el modo de alto contraste.
 *
 * Los componentes que usan colores propios (por ejemplo los colores de usuario
 * de Twitch/Kick) pueden consultarlo para priorizar la legibilidad.
 */
val LocalHighContrast = staticCompositionLocalOf { false }

/**
 * Indica si el tema efectivo es oscuro, teniendo en cuenta la elección del
 * usuario (Sistema / Claro / Oscuro), no solo el ajuste del dispositivo.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * Alto contraste en claro: fondos blancos, texto negro y primario morado muy
 * oscuro. Se ignoran los colores dinámicos porque podrían reducir el contraste.
 */
private val HighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF3F1D8F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9CCF0),
    onPrimaryContainer = Color(0xFF000000),
    inversePrimary = Color(0xFFD1BCFF),
    secondary = Color(0xFF413B4A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4DFE9),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFF6E1B3C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8D6E1),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE6E0EC),
    onSurfaceVariant = Color(0xFF1C1B1F),
    surfaceTint = Color(0xFF3F1D8F),
    inverseSurface = Color(0xFF1C1B1F),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFF8C0009),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF000000),
    outline = Color(0xFF000000),
    outlineVariant = Color(0xFF4A4458),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDED8E4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F4FA),
    surfaceContainer = Color(0xFFF2EEF7),
    surfaceContainerHigh = Color(0xFFECE7F2),
    surfaceContainerHighest = Color(0xFFE6E0EC)
)

/**
 * Alto contraste en oscuro: fondos casi negros, texto blanco y primario lavanda
 * claro para mantener una relación de contraste alta.
 */
private val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD6BBFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF4F2B8F),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF3F1D8F),
    secondary = Color(0xFFD8CCE8),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF413B4A),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFFB2C8),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF6E1B3C),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A2433),
    onSurfaceVariant = Color(0xFFFFFFFF),
    surfaceTint = Color(0xFFD6BBFF),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF8C0009),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFFCFC6DE),
    outlineVariant = Color(0xFF8F86A3),
    surfaceBright = Color(0xFF1F1B27),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0810),
    surfaceContainer = Color(0xFF12101A),
    surfaceContainerHigh = Color(0xFF1B1826),
    surfaceContainerHighest = Color(0xFF252031)
)

@Composable
fun ChatMultiStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    // 1f = tamaño de fuente del sistema. Los ajustes de la app lo multiplican.
    fontScale: Float = 1f,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        highContrast && darkTheme -> HighContrastDarkColorScheme
        highContrast -> HighContrastLightColorScheme

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // LocalDensity permite escalar TODO el texto (Material y tamaños en sp
    // propios) sin tocar cada estilo. Se recuerda para no recrear la densidad
    // en cada recomposición.
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, fontScale) {
        Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * fontScale.coerceIn(0.5f, 2f)
        )
    }

    CompositionLocalProvider(
        LocalHighContrast provides highContrast,
        LocalDarkTheme provides darkTheme,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
