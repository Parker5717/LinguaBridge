package com.linguabridge.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.linguabridge.app.R

// Lora (variable, OFL) carries the editorial "reading room" voice in
// headlines and word cards; the platform sans stays for dense body text.
@OptIn(ExperimentalTextApi::class)
private val Lora = FontFamily(
    Font(R.font.lora, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.lora, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.lora, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.lora, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val Sans = FontFamily.SansSerif

val LinguaTypography = Typography(
    displayLarge = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, lineHeight = 1.15.em),
    displayMedium = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 1.15.em),
    displaySmall = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 1.2.em),
    headlineLarge = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 1.2.em),
    headlineMedium = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 1.25.em),
    headlineSmall = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Medium, fontSize = 23.sp, lineHeight = 1.25.em),
    titleLarge = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 1.3.em),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 1.4.em, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 1.4.em, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 1.5.em, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 1.5.em, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 1.45.em, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 1.35.em, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 1.35.em, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 1.3.em, letterSpacing = 0.4.sp),
)
