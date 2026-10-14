plugins {
    alias(libs.plugins.flickq.feature.presentation)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.reservation.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.reservation.domain)
            implementation(libs.kotlin.datetime)
            implementation(libs.bundles.paging.libs)
        }
    }
}