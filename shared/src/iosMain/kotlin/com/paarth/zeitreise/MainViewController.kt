package com.paarth.zeitreise

import androidx.compose.ui.window.ComposeUIViewController
import com.paarth.zeitreise.ui.App
import platform.UIKit.UIViewController

/** Creates the view controller that shows the shared Compose UI in the iOS app. */
public fun mainViewController(): UIViewController = ComposeUIViewController { App() }
