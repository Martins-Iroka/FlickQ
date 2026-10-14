plugins {
    alias(libs.plugins.flickq.feature.data)
}

kotlin {

    android {
        namespace = "com.martdev.flickq.feature.reservation.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.reservation.domain)
        }
    }
}