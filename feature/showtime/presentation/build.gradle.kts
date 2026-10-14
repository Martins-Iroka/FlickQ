plugins {
    alias(libs.plugins.flickq.feature.presentation)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.showtime.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.showtime.domain)
            implementation(libs.kotlin.datetime)
            implementation(libs.kotlinx.collection.immutable)
        }
    }
}
