plugins {
    alias(libs.plugins.flickq.kmp.library)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.showtime.domain"
        compileSdk { version = release(36) }
    }
}
