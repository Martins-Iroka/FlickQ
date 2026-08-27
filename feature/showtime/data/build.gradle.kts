plugins {
    alias(libs.plugins.flickq.kmp.library)
}

kotlin {

    android {
        namespace = "com.martdev.flickq.feature.showtime.data"
        compileSdk { version = release(36) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.showtime.domain)
        }
    }
}
