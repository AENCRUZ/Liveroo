package com.group4.liveroo.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Material colour scheme filled from the Liveroo tokens, so stock components
// (dialogs, menus, text fields, sheets, snackbars) match the design in both themes.
private fun schemeFrom(c: LiverooColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accentContainer,
        onPrimaryContainer = c.onAccentContainer,
        secondary = c.statusProgressFill,
        onSecondary = c.onStatusDone,
        secondaryContainer = c.accentContainer,
        onSecondaryContainer = c.onAccentContainer,
        tertiary = c.statusDone,
        onTertiary = c.onStatusDone,
        background = c.background,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceTile,
        onSurfaceVariant = c.textSecondary,
        surfaceTint = c.surface,
        surfaceBright = c.surface,
        surfaceDim = c.surface,
        surfaceContainerLowest = c.surface,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.surface,
        inverseSurface = c.snackbar,
        inverseOnSurface = c.onSnackbar,
        inversePrimary = c.accentContainer,
        outline = c.outlineControl,
        outlineVariant = c.divider,
        error = c.error,
        onError = c.onAccent,
        errorContainer = c.errorSurface,
        onErrorContainer = c.error,
        scrim = c.scrim
    )
}

private val LiverooShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),    // fields, banners, segmented control
    medium = RoundedCornerShape(20.dp),   // cards
    large = RoundedCornerShape(24.dp),    // dialogs, hero cards
    extraLarge = RoundedCornerShape(28.dp) // sheets
)

val LocalLiverooTextStyles = staticCompositionLocalOf { DefaultLiverooTextStyles }

/**
 * Light by default; dark only when the user turns on "Dark mode" in Settings (addendum 01).
 * The phone's own dark-mode setting is ignored.
 */
@Composable
fun LiverooTheme(
    darkTheme: Boolean = rememberDarkMode(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkLiverooColors else LightLiverooColors
    // enableEdgeToEdge() picks system-bar icon colours from the phone's setting, so follow the in-app one here.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(
        LocalLiverooColors provides colors,
        LocalLiverooTextStyles provides DefaultLiverooTextStyles
    ) {
        MaterialTheme(
            colorScheme = schemeFrom(colors),
            typography = LiverooTypography,
            shapes = LiverooShapes,
            content = content
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Access point for Liveroo tokens: LiverooTheme.colors.accent, LiverooTheme.type.heading. */
object LiverooTheme {
    val colors: LiverooColors
        @Composable @ReadOnlyComposable get() = LocalLiverooColors.current
    val type: LiverooTextStyles
        @Composable @ReadOnlyComposable get() = LocalLiverooTextStyles.current
}
