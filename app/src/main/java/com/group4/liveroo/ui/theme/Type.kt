@file:OptIn(ExperimentalTextApi::class)

package com.group4.liveroo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.group4.liveroo.R

// Headings: Luckiest Guy (one weight). Never use it for body text, buttons or chips.
val LuckiestGuy = FontFamily(Font(R.font.luckiest_guy, FontWeight.Normal))

// Body: Comfortaa variable font (weights 300-700), bundled in res/font.
private fun comfortaa(weight: Int) = Font(
    R.font.comfortaa_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Comfortaa = FontFamily(
    comfortaa(400),
    comfortaa(500),
    comfortaa(600),
    comfortaa(700)
)

// Sizes and weights from the spec (C23). Body is one step heavier than usual: Comfortaa looks thin on phones.
val LiverooTypography = Typography(
    headlineLarge = TextStyle(fontFamily = LuckiestGuy, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = 0.5.sp),
    headlineMedium = TextStyle(fontFamily = LuckiestGuy, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = 0.5.sp), // screen titles
    titleLarge = TextStyle(fontFamily = LuckiestGuy, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),     // section headers
    titleMedium = TextStyle(fontFamily = Comfortaa, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp), // group names on cards
    titleSmall = TextStyle(fontFamily = Comfortaa, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),  // task titles
    bodyLarge = TextStyle(fontFamily = Comfortaa, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Comfortaa, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Comfortaa, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Comfortaa, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),  // buttons
    labelMedium = TextStyle(fontFamily = Comfortaa, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp), // chips
    labelSmall = TextStyle(fontFamily = Comfortaa, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp)   // tags, nav labels
)
