package com.tva.missminutes.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import com.tva.missminutes.R

// ── Google Fonts Provider ─────────────────────────────────────────────────
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// ── Courier Prime — TVA's institutional monospace feel ───────────────────
private val CourierPrimeFont = GoogleFont("Courier Prime")
val CourierPrimeFontFamily = FontFamily(
    Font(googleFont = CourierPrimeFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = CourierPrimeFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = CourierPrimeFont, fontProvider = provider, weight = FontWeight.Normal, style = FontStyle.Italic),
)

// ── Orbitron — Retro-futuristic headings ─────────────────────────────────
private val OrbitronFont = GoogleFont("Orbitron")
val OrbitronFontFamily = FontFamily(
    Font(googleFont = OrbitronFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = OrbitronFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = OrbitronFont, fontProvider = provider, weight = FontWeight.SemiBold),
)

// ── Material3 Typography ──────────────────────────────────────────────────
val MissMinutesTypography = Typography(
    // App title / Miss Minutes name
    displayLarge = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 4.sp,
        color = TVAAmber
    ),
    displayMedium = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 2.sp,
        color = TVAAmber
    ),
    // Status text ("Listening...", "Thinking...")
    titleLarge = TextStyle(
        fontFamily = CourierPrimeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 2.sp,
        color = TVAAmber
    ),
    titleMedium = TextStyle(
        fontFamily = CourierPrimeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 1.sp,
        color = TVAWarmOrange
    ),
    // Body / transcript text
    bodyLarge = TextStyle(
        fontFamily = CourierPrimeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp,
        color = TVAAmber
    ),
    bodyMedium = TextStyle(
        fontFamily = CourierPrimeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = TVAMuted
    ),
    bodySmall = TextStyle(
        fontFamily = CourierPrimeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
        color = TVADimmed
    ),
    labelLarge = TextStyle(
        fontFamily = OrbitronFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.5.sp,
        color = TVAAmber
    ),
    labelSmall = TextStyle(
        fontFamily = CourierPrimeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
        color = TVADimmed
    )
)
