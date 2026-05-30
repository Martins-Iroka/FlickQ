package com.martdev.flickq

import androidx.compose.ui.window.ComposeUIViewController
import com.martdev.flickq.di.initKoin
import org.koin.core.context.GlobalContext
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    if (GlobalContext.getOrNull() == null) {
        initKoin()
    }
    return ComposeUIViewController { FlickQApp() }
}
