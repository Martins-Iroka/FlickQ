plugins {
    alias(libs.plugins.flickq.feature.presentation)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.auth.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
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
