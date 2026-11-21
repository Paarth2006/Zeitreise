package com.paarth.zeitreise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.paarth.zeitreise.di.sharedModule
import com.paarth.zeitreise.ui.globe.GlobeScreen
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

/**
 * The root composable, used by the Android, iOS and web entry points.
 *
 * Koin is started here instead of in each platform, so every entry point is just one call. There is
 * no preview because it needs Koin and the view models.
 *
 * @param modifier applied to the root element of the screen.
 */
@Composable
public fun App(modifier: Modifier = Modifier) {
    KoinApplication(configuration = koinConfiguration { modules(sharedModule) }) {
        ZeitreiseTheme {
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                // The globe draws behind the system bars; the controls handle their own insets.
                GlobeScreen()
            }
        }
    }
}
