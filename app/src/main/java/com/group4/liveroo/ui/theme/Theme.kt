package com.group4.liveroo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LiverooColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = White,
    primaryContainer = SelectedTint,
    onPrimaryContainer = Ink,
    secondary = MutedSage,
    onSecondary = Ink,
    secondaryContainer = MutedSage,
    onSecondaryContainer = Ink,
    tertiary = FreshGreen,
    onTertiary = Ink,
    background = WarmCream,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = SoftPeach,
    onSurfaceVariant = SecondaryText,
    surfaceContainer = White,
    surfaceContainerHigh = White,
    surfaceContainerHighest = White,
    outline = Border,
    outlineVariant = Border,
    error = ErrorTerracotta,
    onError = White
)

private val LiverooShapes = Shapes(
    small = RoundedCornerShape(12.dp),   // buttons, text fields
    medium = RoundedCornerShape(16.dp),  // cards
    large = RoundedCornerShape(24.dp),   // sheets, dialogs
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LiverooTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiverooColors,
        typography = LiverooTypography,
        shapes = LiverooShapes,
        content = content
    )
}
