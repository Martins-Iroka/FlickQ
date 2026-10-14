plugins {
    alias(libs.plugins.flickq.kmp.library)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.payment.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }
}
