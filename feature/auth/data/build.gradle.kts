plugins {
    alias(libs.plugins.flickq.feature.data)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.auth.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.auth.domain)
        }
    }
}
