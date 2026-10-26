package com.martdev.flickq

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    // Normally already started by the AppDelegate; the guard keeps previews and tests safe.
    startKoinIfNeeded()
    return ComposeUIViewController { FlickQApp() }
}