import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "com.martdev.flickq.feature.auth.data"
        compileSdk { version = release(36) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.auth.domain)
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.api)
            implementation(libs.ktor.client.core)
            implementation(libs.koin.core)
            implementation(libs.kotlin.datetime)
        }
        commonTest.dependencies {
            implementation(libs.bundles.client.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}
