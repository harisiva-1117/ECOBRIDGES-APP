package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ===========================================================================
// ECOBRIDGES design tokens
// White + very light mint background, deep eco green, soft green/blue/orange
// cards, dark navy-green text. Centralized here — UI code should reference
// these tokens and NOT scatter ad-hoc hex colours.
// ===========================================================================

// Primary greens
val EcoGreenPrimary = Color(0xFF006B4F)   // primary dark green
val EcoGreenDeep = Color(0xFF004D3A)      // deep green
val EcoGreenBright = Color(0xFF0B8F68)    // bright / medium green
val EcoGreenSoft = Color(0xFF34C48D)      // soft bright accent

// Mints
val EcoMint = Color(0xFFE8F7F0)           // mint
val EcoMintLight = Color(0xFFF2FBF7)      // very light mint (page background)

// Blue family (stat cards)
val EcoBlueBg = Color(0xFFEAF4FF)
val EcoBlueAccent = Color(0xFF1687D9)

// Orange family (pending / warnings)
val EcoOrangeBg = Color(0xFFFFF3E6)
val EcoOrangeAccent = Color(0xFFF28C00)

// Text
val EcoTextPrimary = Color(0xFF12343B)    // main text (dark navy/green)
val EcoTextSecondary = Color(0xFF687984)  // secondary text
val EcoTextTertiary = Color(0xFF8AA0AB)   // muted

// Surfaces & lines
val EcoWhite = Color(0xFFFFFFFF)
val EcoBorder = Color(0xFFD8EEE6)         // subtle green borders

// ---------------------------------------------------------------------------
// Legacy semantic names mapped onto the ECOBRIDGES palette. Keeping these
// aliases lets the whole existing codebase adopt the new identity in one pass
// instead of editing every colour literal across ~20 files.
// ---------------------------------------------------------------------------
val ForestGreenPrimary = EcoGreenPrimary
val ForestGreenDark = EcoGreenDeep
val ForestGreenLight = EcoGreenBright
val EmeraldAccent = EcoGreenBright
val EmeraldSoft = EcoGreenSoft
val MintLight = EcoMint
val MintPill = Color(0xFFE0F3E9)
val MintBorder = EcoBorder

val SurfaceWarmWhite = Color(0xFFFAFCFA)
val BackgroundCream = EcoMintLight
val CardSurfaceWhite = EcoWhite

val TextPrimaryDark = EcoTextPrimary
val TextSecondaryMuted = EcoTextSecondary
val TextTertiary = EcoTextTertiary

val WarningAmber = EcoOrangeAccent
val ErrorRed = Color(0xFFDC2626)
val SuccessGreen = Color(0xFF15803D)