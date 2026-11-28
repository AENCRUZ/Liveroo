package com.group4.liveroo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Liveroo colour tokens (handoff spec, "Colour and theme tokens").
 * One instance for light, one for dark; read them with LiverooTheme.colors.
 */
@Immutable
data class LiverooColors(
    val background: Color,
    val surface: Color,
    val surfaceTile: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val statusProgress: Color,      // In progress icon
    val statusProgressFill: Color,  // MutedSage fill
    val statusDone: Color,          // Done fill
    val statusDoneIcon: Color,      // Done icon on cards (3:1 on white and tiles)
    val onStatusDone: Color,        // check mark on the Done fill
    val pendingSurface: Color,      // "waiting to be shared" (never an error)
    val pendingText: Color,
    val error: Color,
    val errorSurface: Color,
    val divider: Color,
    val outlineControl: Color,      // 3:1 borders for fields, chips, segments
    val navBar: Color,
    val navBorder: Color,
    val navInactive: Color,
    val navBadge: Color,
    val onNavBadge: Color,
    val snackbar: Color,
    val onSnackbar: Color,
    val scrim: Color,
    val isDark: Boolean
)

val LightLiverooColors = LiverooColors(
    background = Color(0xFFF4EBD0),
    surface = Color(0xFFFFFFFF),
    surfaceTile = Color(0xFFFAF6EA),
    textPrimary = Color(0xFF1F2A1C),
    textSecondary = Color(0xFF4A5547),
    accent = Color(0xFF2A7C13),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFE8F2E0),
    onAccentContainer = Color(0xFF1F2A1C),
    statusProgress = Color(0xFF2F6B57),
    statusProgressFill = Color(0xFF90BEAB),
    statusDone = Color(0xFF76C457),
    statusDoneIcon = Color(0xFF589341),
    onStatusDone = Color(0xFF1F2A1C),
    pendingSurface = Color(0xFFFFF1D6),
    pendingText = Color(0xFF7A4F00),
    error = Color(0xFFA3402B),
    errorSurface = Color(0xFFFBEBE7),
    divider = Color(0xFFEDE5CF),
    outlineControl = Color(0xFF7F8474),
    navBar = Color(0xFFFFFFFF),
    navBorder = Color(0xFFE3D9BC),
    navInactive = Color(0xFF4A5547),
    navBadge = Color(0xFFF2C46B),
    onNavBadge = Color(0xFF3A2C0E),
    snackbar = Color(0xFF1F2620),
    onSnackbar = Color(0xFFF4EBD0),
    scrim = Color(0x7A0A0E0A),
    isDark = false
)

val DarkLiverooColors = LiverooColors(
    background = Color(0xFF121813),
    surface = Color(0xFF1B221C),
    surfaceTile = Color(0xFF232B24),
    textPrimary = Color(0xFFEEF2EA),
    textSecondary = Color(0xFFA9B5A6),
    accent = Color(0xFF76C457),
    onAccent = Color(0xFF10200B),
    accentContainer = Color(0xFF1E3318),
    onAccentContainer = Color(0xFF9BDB7F),
    statusProgress = Color(0xFF90BEAB),
    statusProgressFill = Color(0xFF90BEAB),
    statusDone = Color(0xFF76C457),
    statusDoneIcon = Color(0xFF76C457),
    onStatusDone = Color(0xFF10200B),
    pendingSurface = Color(0xFF3A2C0E),
    pendingText = Color(0xFFF2C46B),
    error = Color(0xFFF09A85),
    errorSurface = Color(0xFF3A1A14),
    divider = Color(0xFF2A332B),
    outlineControl = Color(0xFF6B786E),
    navBar = Color(0xFF263027),
    navBorder = Color(0xFF2E3A2F),
    navInactive = Color(0xFFC9D3C6),
    navBadge = Color(0xFFF2C46B),
    onNavBadge = Color(0xFF3A2C0E),
    snackbar = Color(0xFFEEF2EA),
    onSnackbar = Color(0xFF1B221C),
    scrim = Color(0x7A0A0E0A),
    isDark = true
)

val LocalLiverooColors = staticCompositionLocalOf { LightLiverooColors }

/** Avatar tints. Initials are always AvatarInk, in light and dark. */
val AvatarPalette = listOf(
    Color(0xFFF6C9A8), Color(0xFFF3B9B0), Color(0xFFF5D98C), Color(0xFFE3C6E8),
    Color(0xFFB9D9EE), Color(0xFFB8E0CF), Color(0xFFCFE3A6), Color(0xFFD4CCF2)
)
val AvatarInk = Color(0xFF1F2A1C)

/** Same name, same colour, on every phone. */
fun avatarColor(name: String): Color = AvatarPalette[Math.floorMod(name.hashCode(), AvatarPalette.size)]
