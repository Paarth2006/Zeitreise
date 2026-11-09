package com.paarth.zeitreise

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.paarth.zeitreise.ui.App

/** Entry point of the web app; mounts the shared Compose UI into the page body. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App()
    }
}
