package com.paarth.zeitreise.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The dark background behind the globe. */
private val NIGHT_SKY = Color(0xFF070B14)

/** Accent colour for buttons and links (iOS system blue). */
private val SYSTEM_BLUE = Color(0xFF007AFF)

/** Text colour on the dark background, like on the welcome screen. */
private val STARLIGHT = Color(0xFFF5F5F7)

/** Opacity of secondary text on the glass panels. */
internal const val SECONDARY_TEXT_ALPHA = 0.6f

/** The app's colours: a dark background, light glass panels with black text, and a blue accent. */
private val ZEITREISE_COLORS = darkColorScheme(
    primary = SYSTEM_BLUE,
    onPrimary = Color.White,
    background = NIGHT_SKY,
    onBackground = STARLIGHT,
    surface = Color.White,
    onSurface = Color.Black,
)

/** Text styles based on Apple's type scale, mapped to Material's roles so all platforms match. */
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

/** Applies the app's colours and text styles to [content]. */
@Composable
public fun ZeitreiseTheme(content: @Composable () -> Unit): Unit = MaterialTheme(
    colorScheme = ZEITREISE_COLORS,
    typography = ZEITREISE_TYPOGRAPHY,
    content = content,
)
