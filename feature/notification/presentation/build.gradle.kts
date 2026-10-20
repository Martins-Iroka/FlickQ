plugins {
    alias(libs.plugins.flickq.feature.presentation)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.notification.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.notification.domain)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}