plugins {
    alias(libs.plugins.flickq.kmp.library)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.movie.domain"
        compileSdk { version = release(36) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.datetime)
        }
    }
}
