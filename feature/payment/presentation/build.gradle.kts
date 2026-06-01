import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
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
        namespace = "com.martdev.flickq.feature.payment.presentation"
        compileSdk { version = release(36) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.payment.domain)
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.presentation)
            implementation(projects.core.designSystem)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.bundles.client.koin.compose)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.browser)
        }
        commonTest.dependencies {
            implementation(libs.bundles.client.test)
        }
    }
}
