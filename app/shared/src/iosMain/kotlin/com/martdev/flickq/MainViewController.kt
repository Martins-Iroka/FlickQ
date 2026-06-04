package com.martdev.flickq

import androidx.compose.ui.window.ComposeUIViewController
import com.martdev.flickq.di.initKoin
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    if (KoinPlatform.getKoinOrNull() == null) {
        initKoin()
    }
    return ComposeUIViewController { FlickQApp() }
}
