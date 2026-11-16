package com.paarth.zeitreise.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Deep night sky the globe sits against; the app is dark by default on every platform. */
private val NIGHT_SKY = Color(0xFF070B14)

/** Apple's system blue, the accent for buttons and links on light glass. */
private val SYSTEM_BLUE = Color(0xFF007AFF)

/** Text over the night sky itself, such as the welcome screen. */
private val STARLIGHT = Color(0xFFF5F5F7)

/**
 * Opacity of secondary text: black at this opacity matches the secondary label colour of
 * Apple's light appearance, which is what the glass panels use.
 */
internal const val SECONDARY_TEXT_ALPHA = 0.6f

/**
 * Space behind the globe is dark, but every panel over it is light Liquid Glass with black labels,
 * as Apple Maps draws its controls over a satellite map: dark backdrop, light glass, blue accent.
 */
private val ZEITREISE_COLORS = darkColorScheme(
    primary = SYSTEM_BLUE,
    onPrimary = Color.White,
    background = NIGHT_SKY,
    onBackground = STARLIGHT,
    surface = Color.White,
    onSurface = Color.Black,
)

/**
 * Sizes and weights from the Apple Human Interface Guidelines' default type scale, mapped onto the
 * Material roles the app uses, so text has the same hierarchy on Android, iOS and the web.
 */
private val ZEITREISE_TYPOGRAPHY = Typography(
    // Large Title
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    // Title 2
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    // Headline
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    // Body
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    // Subheadline
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    // Footnote
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
)

/**
 * Applies the Zeitreise colour scheme and type scale to [content].
 *
 * Wraps every platform entry point, so the dark palette and the type scale are defined once and
 * the three apps share them.
 */
@Composable
public fun ZeitreiseTheme(content: @Composable () -> Unit): Unit = MaterialTheme(
    colorScheme = ZEITREISE_COLORS,
    typography = ZEITREISE_TYPOGRAPHY,
    content = content,
)
