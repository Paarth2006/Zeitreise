package com.paarth.zeitreise

import androidx.compose.ui.window.ComposeUIViewController
import com.paarth.zeitreise.ui.App
import platform.UIKit.UIViewController

/** Hosts the shared Compose UI inside the SwiftUI wrapper in `iosApp`. */
public fun mainViewController(): UIViewController = ComposeUIViewController { App() }
