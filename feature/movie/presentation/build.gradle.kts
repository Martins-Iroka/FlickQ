plugins {
    alias(libs.plugins.flickq.feature.presentation)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.movie.presentation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.movie.domain)
            implementation(libs.kotlin.datetime)
            implementation(libs.bundles.paging.libs)
        }
    }
}
