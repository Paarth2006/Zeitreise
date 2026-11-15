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
 * Root composable of the app, shared by the Android, iOS and web entry points.
 *
 * Starts Koin for the composition rather than at platform start-up, so each entry point is only
 * one call and the three platforms cannot drift apart.
 *
 * Not previewed: it starts Koin and the view models, which need a running app. The stateless
 * components it is built from have previews of their own.
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
                // The globe runs under the system bars; the screen keeps its own controls clear of them.
                GlobeScreen()
            }
        }
    }
}
