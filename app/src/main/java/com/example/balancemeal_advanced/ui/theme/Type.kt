package com.example.balancemeal_advanced.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Stitch Noto Sans binaries were unavailable. Keep platform sans fallback explicit;
// sizes/line heights/weights come from the archived screen HTML.
private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, letterSpacing = 0.sp,
)
val Typography = Typography(
    displayLarge = style(36, 44, FontWeight.Bold),
    displayMedium = style(32, 40, FontWeight.Bold),
    headlineLarge = style(26, 34, FontWeight.Bold),
    headlineMedium = style(22, 30, FontWeight.SemiBold),
    headlineSmall = style(18, 26, FontWeight.SemiBold),
    titleLarge = style(18, 26, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium),
)
