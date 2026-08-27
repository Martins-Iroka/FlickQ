plugins {
    alias(libs.plugins.flickq.feature.data)
}

kotlin {
    android {
        namespace = "com.martdev.flickq.feature.payment.data"
        compileSdk { version = release(36) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.payment.domain)
        }
    }
}
