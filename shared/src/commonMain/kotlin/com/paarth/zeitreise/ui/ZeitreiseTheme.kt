package com.paarth.zeitreise.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Deep night sky the globe sits against; the app is dark by default on every platform. */
private val NIGHT_SKY = Color(0xFF070B14)

/** Pale gold used for the selected country, its border, and the text on the sheet. */
private val STARLIGHT = Color(0xFFF2D9A0)

/** Panels such as the country sheet, lifted just clear of the background so they read as panels. */
private val PANEL = Color(0xFF131E31)

private val ZEITREISE_COLORS = darkColorScheme(
    primary = STARLIGHT,
    onPrimary = NIGHT_SKY,
    background = NIGHT_SKY,
    onBackground = STARLIGHT,
    surface = PANEL,
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
