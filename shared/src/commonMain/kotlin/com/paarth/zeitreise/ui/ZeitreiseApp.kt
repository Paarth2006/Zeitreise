package com.paarth.zeitreise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/** Title shown on the placeholder screen and used as the app name on every platform. */
public const val APP_NAME: String = "Zeitreise"

/**
 * Root composable of the app, shared by the Android, iOS and web entry points.
 *
 * @param modifier applied to the root element of the screen.
 */
@Composable
@Preview
public fun App(modifier: Modifier = Modifier) {
    ZeitreiseTheme {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = APP_NAME,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
