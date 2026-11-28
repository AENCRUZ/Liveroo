package com.group4.liveroo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.group4.liveroo.R

// Brand moments only (splash, onboarding headlines, "Synced with ...", "You joined").
// Never in lists, buttons, tags, dialogs or text longer than about 6 words.
val LuckiestGuy = FontFamily(Font(R.font.luckiest_guy, FontWeight.Normal))

// All regular UI text: titles, headings, body, buttons, navigation.
val Nunito = FontFamily(
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold)
)

private fun nunito(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Nunito, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

/** Named styles from the handoff spec ("Typography"). Read them with LiverooTheme.type. */
@Immutable
data class LiverooTextStyles(
    val brandDisplay: TextStyle = TextStyle(fontFamily = LuckiestGuy, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = 0.5.sp),
    val brandHeadline: TextStyle = TextStyle(fontFamily = LuckiestGuy, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = 0.5.sp),
    val titleLarge: TextStyle = nunito(28, 34, FontWeight.ExtraBold),   // tab screen titles
    val titleMedium: TextStyle = nunito(22, 28, FontWeight.ExtraBold),  // top bars, group and task titles
    val dialogTitle: TextStyle = nunito(20, 26, FontWeight.ExtraBold),  // dialogs, empty states
    val heading: TextStyle = nunito(17, 24, FontWeight.Bold),           // section headers
    val cardTitle: TextStyle = nunito(17, 24, FontWeight.ExtraBold),    // group names on cards
    val itemTitle: TextStyle = nunito(16, 22, FontWeight.Bold),         // row titles
    val bodyLarge: TextStyle = nunito(16, 24, FontWeight.Medium),
    val body: TextStyle = nunito(14, 20, FontWeight.Medium),
    val label: TextStyle = nunito(15, 20, FontWeight.Bold),             // "Status", "Assignee"
    val caption: TextStyle = nunito(13, 18, FontWeight.Medium),
    val button: TextStyle = nunito(15, 20, FontWeight.Bold),
    val chip: TextStyle = nunito(13, 18, FontWeight.Bold),              // chips, tags, nav labels
    val micro: TextStyle = nunito(12, 16, FontWeight.Bold)
)

val DefaultLiverooTextStyles = LiverooTextStyles()

// Material slots, filled from the named styles so stock components and screens not yet
// redesigned pick up Nunito. headlineLarge stays the brand headline (onboarding titles).
val LiverooTypography = Typography(
    displayLarge = DefaultLiverooTextStyles.brandDisplay,
    headlineLarge = DefaultLiverooTextStyles.brandHeadline,
    headlineMedium = DefaultLiverooTextStyles.titleMedium,
    headlineSmall = DefaultLiverooTextStyles.dialogTitle,
    titleLarge = DefaultLiverooTextStyles.heading,
    titleMedium = DefaultLiverooTextStyles.cardTitle,
    titleSmall = DefaultLiverooTextStyles.itemTitle,
    bodyLarge = DefaultLiverooTextStyles.bodyLarge,
    bodyMedium = DefaultLiverooTextStyles.body,
    bodySmall = DefaultLiverooTextStyles.caption,
    labelLarge = DefaultLiverooTextStyles.button,
    labelMedium = DefaultLiverooTextStyles.chip,
    labelSmall = DefaultLiverooTextStyles.micro
)
