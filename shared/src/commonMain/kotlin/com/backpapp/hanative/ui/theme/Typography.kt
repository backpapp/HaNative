package com.backpapp.hanative.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hanative.shared.generated.resources.Res
import hanative.shared.generated.resources.inter_bold
import hanative.shared.generated.resources.inter_extrabold
import hanative.shared.generated.resources.inter_medium
import hanative.shared.generated.resources.inter_regular
import hanative.shared.generated.resources.inter_semibold
import org.jetbrains.compose.resources.Font

@Composable
fun interFontFamily() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
    Font(Res.font.inter_extrabold, FontWeight.ExtraBold),
)

@Composable
fun dashboardNameStyle(inter: FontFamily) = TextStyle(
    fontFamily = inter,
    fontSize = 20.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.02).em,
)

@Composable
fun cardValueStyle(inter: FontFamily) = TextStyle(
    fontFamily = inter,
    fontSize = 13.sp,
    fontWeight = FontWeight.ExtraBold,
)

@Composable
fun cardLabelStyle(inter: FontFamily) = TextStyle(
    fontFamily = inter,
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
)

@Composable
fun statusMetadataStyle(inter: FontFamily) = TextStyle(
    fontFamily = inter,
    fontSize = 10.sp,
    fontWeight = FontWeight.SemiBold,
)

@Composable
fun navLabelStyle(inter: FontFamily) = TextStyle(
    fontFamily = inter,
    fontSize = 9.sp,
    fontWeight = FontWeight.Bold,
)

@Composable
fun tempLargeValueStyle(inter: FontFamily) = TextStyle(
    fontFamily = inter,
    fontSize = 20.sp,
    fontWeight = FontWeight.ExtraBold,
)

@Composable
fun haNativeTypography(): Typography {
    val inter = interFontFamily()
    return Typography(
        displayLarge = dashboardNameStyle(inter).copy(fontSize = 57.sp),
        displayMedium = dashboardNameStyle(inter).copy(fontSize = 45.sp),
        displaySmall = tempLargeValueStyle(inter).copy(fontSize = 36.sp),
        headlineLarge = dashboardNameStyle(inter).copy(fontSize = 32.sp),
        headlineMedium = dashboardNameStyle(inter).copy(fontSize = 28.sp),
        headlineSmall = dashboardNameStyle(inter),
        titleLarge = dashboardNameStyle(inter),
        titleMedium = cardValueStyle(inter).copy(fontSize = 16.sp),
        titleSmall = cardValueStyle(inter),
        bodyLarge = cardLabelStyle(inter).copy(fontSize = 16.sp),
        bodyMedium = cardLabelStyle(inter).copy(fontSize = 14.sp),
        bodySmall = cardLabelStyle(inter),
        labelLarge = navLabelStyle(inter).copy(fontSize = 14.sp),
        labelMedium = statusMetadataStyle(inter),
        labelSmall = navLabelStyle(inter),
    )
}
