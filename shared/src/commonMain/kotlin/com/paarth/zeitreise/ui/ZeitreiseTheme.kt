package com.paarth.zeitreise.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Deep night sky the globe sits against; the app is dark by default on every platform. */
private val NIGHT_SKY = Color(0xFF070B14)

/** Pale gold used for the title and, later, for country borders and pins. */
private val STARLIGHT = Color(0xFFF2D9A0)

private val ZEITREISE_COLORS = darkColorScheme(
    primary = STARLIGHT,
    onPrimary = NIGHT_SKY,
    background = NIGHT_SKY,
    onBackground = STARLIGHT,
    surface = NIGHT_SKY,
    onSurface = STARLIGHT,
)

/**
 * Applies the Zeitreise colour scheme to [content].
 *
 * Wraps every platform entry point, so the dark palette is defined once and the three apps share it.
 */
@Composable
public fun ZeitreiseTheme(content: @Composable () -> Unit): Unit =
    MaterialTheme(colorScheme = ZEITREISE_COLORS, content = content)
