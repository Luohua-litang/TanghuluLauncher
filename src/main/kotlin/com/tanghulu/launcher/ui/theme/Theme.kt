package com.tanghulu.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** An optional accent color theme. */
data class AccentOption(val name: String, val color: Color)

/** Preset accent colors (grass green is the Minecraft-style default). */
val AccentOptions = listOf(
    AccentOption("Indigo", Color(0xFF3F51B5)),
    AccentOption("Blue", Color(0xFF2196F3)),
    AccentOption("Green", Color(0xFF4CAF50)),
    AccentOption("Orange", Color(0xFFFF9800)),
    AccentOption("Red", Color(0xFFF44336)),
    AccentOption("Teal", Color(0xFF009688)),
)

/** Minecraft grass green / dirt brown. */
val GrassGreen = Color(0xFF4CAF50)
val DirtBrown = Color(0xFF8D6E63)
val DefaultAccent = AccentOptions[2].color

/** The built-in Microsoft YaHei font family. */
@OptIn(ExperimentalTextApi::class)
val MicrosoftYaHei = FontFamily("Microsoft YaHei")

/** Full typography: uses Microsoft YaHei by default. */
val TanghuluTypography = Typography().run {
    copy(
        displayLarge = displayLarge.withFont(),
        displayMedium = displayMedium.withFont(),
        displaySmall = displaySmall.withFont(),
        headlineLarge = headlineLarge.withFont(),
        headlineMedium = headlineMedium.withFont(),
        headlineSmall = headlineSmall.withFont(),
        titleLarge = titleLarge.withFont(),
        titleMedium = titleMedium.withFont(),
        titleSmall = titleSmall.withFont(),
        bodyLarge = bodyLarge.withFont(),
        bodyMedium = bodyMedium.withFont(),
        bodySmall = bodySmall.withFont(),
        labelLarge = labelLarge.withFont(),
        labelMedium = labelMedium.withFont(),
        labelSmall = labelSmall.withFont(),
    )
}

private fun TextStyle.withFont() = merge(TextStyle(fontFamily = MicrosoftYaHei))

/**
 * Build the Material3 color scheme: the primary color follows the user-selected accent.
 */
private fun buildScheme(dark: Boolean, accent: Color) =
    if (dark) darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = blend(accent, Color.Black, 0.72f),
        onPrimaryContainer = blend(accent, Color.White, 0.55f),
        secondary = DirtBrown,
        onSecondary = Color.White,
        secondaryContainer = blend(DirtBrown, Color.Black, 0.7f),
        onSecondaryContainer = blend(DirtBrown, Color.White, 0.7f),
        tertiary = Color(0xFF7E8CE0),
        background = Color(0xFF0E1113),
        onBackground = Color(0xFFE7E9EA),
        surface = Color(0xFF161A1E),
        onSurface = Color(0xFFE7E9EA),
        surfaceVariant = Color(0xFF1E2328),
        onSurfaceVariant = Color(0xFF9AA4AD),
        surfaceContainer = Color(0xFF1B2025),
        surfaceContainerHigh = Color(0xFF20262C),
        surfaceContainerHighest = Color(0xFF262D33),
        outline = Color(0xFF2A3036),
        outlineVariant = Color(0xFF20262C),
        error = Color(0xFFEF5350),
        onError = Color.White,
    ) else lightColorScheme(
        primary = darken(accent, 0.9f),
        onPrimary = Color.White,
        primaryContainer = blend(accent, Color.White, 0.82f),
        onPrimaryContainer = darken(accent, 0.55f),
        secondary = DirtBrown,
        onSecondary = Color.White,
        secondaryContainer = blend(DirtBrown, Color.White, 0.85f),
        onSecondaryContainer = darken(DirtBrown, 0.55f),
        tertiary = Color(0xFF3F51B5),
        background = Color(0xFFF4F6F8),
        onBackground = Color(0xFF1B1F23),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1B1F23),
        surfaceVariant = Color(0xFFEFF2F5),
        onSurfaceVariant = Color(0xFF5C666E),
        surfaceContainer = Color(0xFFF7F9FA),
        surfaceContainerHigh = Color(0xFFEDF0F3),
        surfaceContainerHighest = Color(0xFFE4E8EB),
        outline = Color(0xFFD5DBE0),
        outlineVariant = Color(0xFFE4E8EB),
        error = Color(0xFFE53935),
        onError = Color.White,
    )

/** Linearly blend two colors; [amount] is the weight of other. */
private fun blend(a: Color, b: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f,
    )
}

/** Darken a color (a smaller factor means darker). */
private fun darken(c: Color, factor: Float): Color = blend(c, Color.Black, 1f - factor)

@Composable
fun TanghuluTheme(
    dark: Boolean,
    accent: Color,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = buildScheme(dark, accent),
        typography = TanghuluTypography,
        content = content,
    )
}
