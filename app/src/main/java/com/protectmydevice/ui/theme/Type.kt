package com.protectmydevice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.protectmydevice.R

/** Bricolage Grotesque for everything you read, DM Mono for tags and numbers. Both SIL OFL (see /licenses). */
val Bricolage = FontFamily(
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)
val Mono = FontFamily(Font(R.font.dm_mono_medium, FontWeight.Medium))

private fun display(size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em,
)
private fun text(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Bricolage, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
private fun mono(size: Int, line: Int) = TextStyle(
    fontFamily = Mono, fontWeight = FontWeight.Medium,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.06.em,
)

val ProtectTypography = Typography(
    displayLarge = display(64, 60, -0.04),
    displayMedium = display(48, 46, -0.035),
    displaySmall = display(38, 38, -0.03),
    headlineLarge = display(32, 34, -0.025),
    headlineMedium = display(28, 30, -0.02),
    headlineSmall = display(23, 26, -0.015),
    titleLarge = text(20, 24, FontWeight.Bold),
    titleMedium = text(17, 22, FontWeight.Bold),
    titleSmall = text(15, 20, FontWeight.Bold),
    bodyLarge = text(17, 24, FontWeight.Medium),
    bodyMedium = text(15, 21, FontWeight.Medium),
    bodySmall = text(13, 18, FontWeight.Medium),
    labelLarge = text(16, 20, FontWeight.Bold),
    labelMedium = mono(12, 16),
    labelSmall = mono(11, 14),
)
