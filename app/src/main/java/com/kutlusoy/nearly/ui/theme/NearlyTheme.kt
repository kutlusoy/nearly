package com.kutlusoy.nearly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object NearlyColors {
    val Rose = Color(0xFFFF4D6D)
    val RoseSoft = Color(0xFFFF8FA3)
    val Teal = Color(0xFF12B5A6)
    val Ink = Color(0xFF16151A)
    val Paper = Color(0xFFFFFBFA)
}

private val Light = lightColorScheme(
    primary = NearlyColors.Rose,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1E6),
    onPrimaryContainer = Color(0xFF5C0A1C),
    secondary = NearlyColors.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9F4F0),
    onSecondaryContainer = Color(0xFF00403A),
    background = NearlyColors.Paper,
    onBackground = NearlyColors.Ink,
    surface = NearlyColors.Paper,
    onSurface = NearlyColors.Ink,
    surfaceVariant = Color(0xFFF4ECEE),
    onSurfaceVariant = Color(0xFF5E5A60),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = NearlyColors.RoseSoft,
    onPrimary = Color(0xFF4A0617),
    primaryContainer = Color(0xFF7A1F34),
    onPrimaryContainer = Color(0xFFFFE1E6),
    secondary = Color(0xFF52D6C8),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF1E4F49),
    onSecondaryContainer = Color(0xFFD9F4F0),
    background = NearlyColors.Ink,
    onBackground = Color(0xFFF2EDEE),
    surface = NearlyColors.Ink,
    onSurface = Color(0xFFF2EDEE),
    surfaceVariant = Color(0xFF2B2A30),
    onSurfaceVariant = Color(0xFFCAC4CB),
    error = Color(0xFFFFB4AB),
)

private val NearlyShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
)

// Light is the default, the dark theme is a choice in the settings
enum class ThemeMode { Light, Dark, System }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> isSystemInDarkTheme()
}

@Composable
fun NearlyTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = NearlyShapes, content = content)
}
