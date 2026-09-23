plugins {
    alias(libs.plugins.flickq.feature.presentation)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.auth.presentation"
        compileSdk { version = release(36) }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.biometrics)
            implementation(projects.core.data)
//            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            implementation(projects.feature.auth.domain)
        }
    }
}
