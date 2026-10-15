plugins {
    alias(libs.plugins.flickq.kmp.library)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.notification.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.bundles.client.test)
        }
    }
}