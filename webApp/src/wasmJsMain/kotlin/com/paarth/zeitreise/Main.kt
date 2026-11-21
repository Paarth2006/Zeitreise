package com.paarth.zeitreise

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.paarth.zeitreise.ui.App

/** Web entry point. Shows the shared Compose UI in the page. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App()
    }
}
