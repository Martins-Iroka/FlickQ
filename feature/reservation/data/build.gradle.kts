plugins {
    alias(libs.plugins.flickq.kmp.library)
}

kotlin {

    android {
        namespace = "com.martdev.flickq.feature.reservation.presentation"
        compileSdk { version = release(36) }
    }
}