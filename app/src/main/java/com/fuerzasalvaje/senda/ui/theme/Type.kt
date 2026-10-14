package com.fuerzasalvaje.senda.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.fuerzasalvaje.senda.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val nunito = GoogleFont("Nunito")

/** Nunito downloaded from Google Fonts; falls back to the system font while offline. */
val Nunito = FontFamily(
    Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = nunito, fontProvider = provider, weight = FontWeight.ExtraBold),
)

private val base = Typography()

private fun TextStyle.nunito(weight: FontWeight? = null) =
    copy(fontFamily = Nunito, fontWeight = weight ?: fontWeight)

val Typography = Typography(
    displayLarge = base.displayLarge.nunito(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.nunito(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.nunito(FontWeight.Bold),
    headlineLarge = base.headlineLarge.nunito(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.nunito(FontWeight.Bold),
    headlineSmall = base.headlineSmall.nunito(FontWeight.Bold),
    titleLarge = base.titleLarge.nunito(FontWeight.Bold),
    titleMedium = base.titleMedium.nunito(FontWeight.Bold),
    titleSmall = base.titleSmall.nunito(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.nunito(),
    bodyMedium = base.bodyMedium.nunito(),
    bodySmall = base.bodySmall.nunito(),
    labelLarge = base.labelLarge.nunito(FontWeight.Bold),
    labelMedium = base.labelMedium.nunito(FontWeight.SemiBold),
    labelSmall = base.labelSmall.nunito(FontWeight.SemiBold).copy(fontSize = 11.sp),
)
