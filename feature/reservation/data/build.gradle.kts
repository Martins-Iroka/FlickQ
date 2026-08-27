plugins {
    alias(libs.plugins.flickq.feature.data)
}

kotlin {

    android {
        namespace = "com.martdev.flickq.feature.reservation.presentation"
        compileSdk { version = release(36) }
    }
}