package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Nothing OS Inspired Monochromatic Palette + Restrained Operational Status Accents
val NothingDarkBg = Color(0xFF0B0B0D)
val NothingDarkSurface = Color(0xFF141417)
val NothingDarkSurfaceElevated = Color(0xFF1C1C20)
val NothingDarkText = Color(0xFFF5F5F7)
val NothingDarkSecondaryText = Color(0xFF8E8E93)
val NothingDarkBorder = Color(0xFF26262B)
val NothingDarkBorderSubtle = Color(0xFF1E1E23)

val NothingLightBg = Color(0xFFF6F6F4)
val NothingLightSurface = Color(0xFFFFFFFF)
val NothingLightSurfaceElevated = Color(0xFFECECE9)
val NothingLightText = Color(0xFF101012)
val NothingLightSecondaryText = Color(0xFF636369)
val NothingLightBorder = Color(0xFFDCDCE0)
val NothingLightBorderSubtle = Color(0xFFE7E7EA)

// Restrained Operational Status Accents (Only semantic indicators)
val NothingRed = Color(0xFFE03E36)
val NothingAmber = Color(0xFFD9822B)
val NothingGreen = Color(0xFF2EA063)

// Legacy Compatibility Aliases mapped to Nothing OS tokens
val TaskzSlate950 = NothingDarkBg
val TaskzSlate900 = NothingDarkSurface
val TaskzSlate800 = NothingDarkSurfaceElevated
val TaskzSlate700 = NothingDarkBorder
val TaskzSlate600 = Color(0xFF48484D)
val TaskzSlate500 = NothingLightSecondaryText
val TaskzSlate400 = NothingDarkSecondaryText
val TaskzSlate300 = NothingLightBorder
val TaskzSlate200 = NothingLightSurfaceElevated
val TaskzSlate100 = NothingLightBg
val TaskzSlate50 = NothingLightSurface

val TaskzCyanPrimary = NothingDarkText
val TaskzCyanBright = NothingDarkText
val TaskzCyanLight = NothingDarkSecondaryText
val TaskzCyanDark = NothingLightText
val TaskzCyanContainerDark = NothingDarkSurfaceElevated
val TaskzCyanContainerLight = NothingLightSurfaceElevated

val TaskzSuccess = NothingGreen
val TaskzWarning = NothingAmber
val TaskzError = NothingRed
val TaskzInfo = NothingDarkSecondaryText

// Light Theme Monochrome Palette
val LightPrimary = NothingLightText
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = NothingLightSurfaceElevated
val LightOnPrimaryContainer = NothingLightText
val LightSecondary = NothingLightSecondaryText
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE5E5E2)
val LightOnSecondaryContainer = NothingLightText
val LightBackground = NothingLightBg
val LightOnBackground = NothingLightText
val LightSurface = NothingLightSurface
val LightOnSurface = NothingLightText
val LightSurfaceVariant = NothingLightSurfaceElevated
val LightOnSurfaceVariant = NothingLightSecondaryText
val LightOutline = NothingLightBorder

// Dark Theme Monochrome Palette
val DarkPrimary = NothingDarkText
val DarkOnPrimary = NothingDarkBg
val DarkPrimaryContainer = NothingDarkSurfaceElevated
val DarkOnPrimaryContainer = NothingDarkText
val DarkSecondary = NothingDarkSecondaryText
val DarkOnSecondary = NothingDarkBg
val DarkSecondaryContainer = Color(0xFF222227)
val DarkOnSecondaryContainer = NothingDarkText
val DarkBackground = NothingDarkBg
val DarkOnBackground = NothingDarkText
val DarkSurface = NothingDarkSurface
val DarkOnSurface = NothingDarkText
val DarkSurfaceVariant = NothingDarkSurfaceElevated
val DarkOnSurfaceVariant = NothingDarkSecondaryText
val DarkOutline = NothingDarkBorder
