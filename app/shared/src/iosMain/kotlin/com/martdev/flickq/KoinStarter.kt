package com.martdev.flickq

import com.martdev.flickq.di.initKoin
import org.koin.mp.KoinPlatform

/**
 * Starts Koin once. The AppDelegate calls it first (through [com.martdev.flickq.push.PushBridge])
 * so push callbacks can resolve dependencies before any UI exists.
 */
internal fun startKoinIfNeeded() {
    if (KoinPlatform.getKoinOrNull() == null) {
        initKoin()
    }
}