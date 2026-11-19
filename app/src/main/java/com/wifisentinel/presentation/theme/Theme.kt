package com.wifisentinel.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wifisentinel.domain.model.AppLanguage
import com.wifisentinel.presentation.localization.ArabicStrings
import com.wifisentinel.presentation.localization.EnglishStrings
import com.wifisentinel.presentation.localization.LocalSentinelStrings

@Immutable
data class SentinelExtendedColors(
    val borderCrimson: Color,
    val borderCrimsonStrong: Color,
    val crimsonSurface: Color,
    val statusGreen: Color,
    val statusGreenBg: Color,
    val statusAmber: Color,
    val statusAmberBg: Color,
    val statusRed: Color,
    val statusRedBg: Color,
    val neonAccent: Color,
    val divider: Color,
    val isDark: Boolean
)

val LocalSentinelColors = staticCompositionLocalOf {
    SentinelExtendedColors(
        borderCrimson = SentinelCrimsonBorder,
        borderCrimsonStrong = SentinelCrimsonBorderStrong,
        crimsonSurface = SentinelCrimsonSurface,
        statusGreen = StatusGreen,
        statusGreenBg = StatusGreenBg,
        statusAmber = StatusAmber,
        statusAmberBg = StatusAmberBg,
        statusRed = StatusRed,
        statusRedBg = StatusRedBg,
        neonAccent = SentinelNeonRed,
        divider = SentinelDivider,
        isDark = true
    )
}

private val SentinelDarkColorScheme = darkColorScheme(
    primary = SentinelCrimson,
    onPrimary = SentinelWhite,
    primaryContainer = SentinelCrimsonSurface,
    onPrimaryContainer = SentinelWhite,
    secondary = SentinelNeonRed,
    onSecondary = SentinelWhite,
    secondaryContainer = SentinelCharcoalHighest,
    onSecondaryContainer = SentinelWhite,
    tertiary = StatusAmber,
    onTertiary = SentinelBlack,
    background = SentinelBlack,
    onBackground = SentinelWhite,
    surface = SentinelCharcoal,
    onSurface = SentinelWhite,
    surfaceVariant = SentinelCharcoalElevated,
    onSurfaceVariant = SentinelGray,
    surfaceContainerLowest = SentinelBlack,
    surfaceContainerLow = SentinelCharcoal,
    surfaceContainer = SentinelCharcoalElevated,
    surfaceContainerHigh = SentinelCharcoalHighest,
    surfaceContainerHighest = Color(0xFF252530),
    outline = SentinelCrimsonBorder,
    outlineVariant = SentinelDivider,
    error = StatusRed,
    onError = SentinelWhite
)

private val SentinelLightColorScheme = lightColorScheme(
    primary = SentinelCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE6EA),
    onPrimaryContainer = Color(0xFF5D0816),
    secondary = SentinelNeonRed,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = LightOnSurface,
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightOnSurfaceMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceElevated,
    surfaceContainerHigh = Color(0xFFE4E4EC),
    surfaceContainerHighest = Color(0xFFDADAE4),
    outline = LightCrimsonBorder,
    outlineVariant = LightDivider,
    error = Color(0xFFDC2626),
    onError = Color.White
)

val SentinelShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WifiSentinelTheme(
    darkTheme: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SentinelDarkColorScheme else SentinelLightColorScheme
    val extendedColors = if (darkTheme) {
        SentinelExtendedColors(
            borderCrimson = SentinelCrimsonBorder,
            borderCrimsonStrong = SentinelCrimsonBorderStrong,
            crimsonSurface = SentinelCrimsonSurface,
            statusGreen = StatusGreen,
            statusGreenBg = StatusGreenBg,
            statusAmber = StatusAmber,
            statusAmberBg = StatusAmberBg,
            statusRed = StatusRed,
            statusRedBg = StatusRedBg,
            neonAccent = SentinelNeonRed,
            divider = SentinelDivider,
            isDark = true
        )
    } else {
        SentinelExtendedColors(
            borderCrimson = LightCrimsonBorder,
            borderCrimsonStrong = Color(0x66C8102E),
            crimsonSurface = Color(0xFFFBE9EC),
            statusGreen = Color(0xFF15803D),
            statusGreenBg = Color(0x1815803D),
            statusAmber = Color(0xFFB45309),
            statusAmberBg = Color(0x18B45309),
            statusRed = Color(0xFFDC2626),
            statusRedBg = Color(0x18DC2626),
            neonAccent = SentinelCrimson,
            divider = LightDivider,
            isDark = false
        )
    }

    val layoutDirection = if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr
    val strings = if (language == AppLanguage.ARABIC) ArabicStrings else EnglishStrings

    CompositionLocalProvider(
        LocalSentinelColors provides extendedColors,
        LocalLayoutDirection provides layoutDirection,
        LocalSentinelStrings provides strings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SentinelTypography,
            shapes = SentinelShapes,
            content = content
        )
    }
}
