package com.martdev.flickq

import android.app.Application
import com.martdev.flickq.di.initKoin
import com.martdev.flickq.push.NotificationChannels
import org.koin.android.ext.koin.androidContext

class FlickQApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@FlickQApplication)
        }
        NotificationChannels.create(this)
    }
}
