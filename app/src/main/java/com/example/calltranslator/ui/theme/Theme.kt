package com.example.calltranslator.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.ui.graphics.Color
import com.example.calltranslator.viewmodel.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = PurpleViolet,
    secondary = SoftViolet,
    tertiary = CyanGlow,
    background = DeepCharcoal,
    surface = DarkGrayCard,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    outline = Color.Gray,
    error = Color(0xFFCF6679)
)

private val LightColorScheme = lightColorScheme(
    primary = PurpleViolet,
    secondary = SoftViolet,
    tertiary = GradientBlue,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFF1F1F1),
    outline = Color.LightGray,
    error = Color.Red
)

private val PurpleColorScheme = darkColorScheme(
    primary = PurpleViolet,
    secondary = SoftViolet,
    tertiary = CyanGlow,
    background = Color(0xFF1E122B),
    surface = Color(0xFF2D1B3E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF3B2452),
    outline = SoftViolet,
    error = Color(0xFFCF6679)
)

private val MidnightColorScheme = darkColorScheme(
    primary = ElectricBlue,
    secondary = CyanGlow,
    tertiary = SoftViolet,
    background = MidnightBlue,
    surface = DeepNavy,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = DarkCard,
    outline = ElectricBlue,
    error = StandardRed
)

private val AmoledColorScheme = darkColorScheme(
    primary = SoftPink,
    secondary = PurpleViolet,
    tertiary = CyanGlow,
    background = Color(0xFF000000),
    surface = Color(0xFF121212),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    outline = SoftPink,
    error = StandardRed
)

@Composable
fun CallTranslatorTheme(
    appTheme: AppTheme = AppTheme.DARK,
    typography: Typography = MaterialTheme.typography,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.PURPLE -> PurpleColorScheme
        AppTheme.MIDNIGHT -> MidnightColorScheme
        AppTheme.AMOLED -> AmoledColorScheme
    }

    val darkTheme = appTheme != AppTheme.LIGHT

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
