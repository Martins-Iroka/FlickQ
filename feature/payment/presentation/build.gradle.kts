plugins {
    alias(libs.plugins.flickq.feature.presentation)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.payment.presentation"
        compileSdk { version = release(36) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.payment.domain)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.browser)
        }
    }
}
