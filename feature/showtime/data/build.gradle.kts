plugins {
    alias(libs.plugins.flickq.feature.data)
}

kotlin {

    android {
        namespace = "com.martdev.flickq.feature.showtime.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.showtime.domain)
        }
    }
}
