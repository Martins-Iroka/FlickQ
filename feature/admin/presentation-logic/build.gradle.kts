import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// UI-agnostic admin presentation logic (MVI ViewModels + State/Action/Event + Koin module).
// Deliberately has NO Compose-UI deps (no material3/foundation/ui) and NO navigation, so it can
// be consumed by BOTH the legacy Compose-Multiplatform :feature:admin:presentation screens AND
// the Kobweb (Compose HTML) admin app. The ViewModels use only coroutines/StateFlow + the KMP
// lifecycle ViewModel; UiText comes from :core:presentation.
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
        namespace = "com.martdev.flickq.feature.admin.presentation.logic"
        compileSdk { version = release(36) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.admin.domain)
            implementation(projects.feature.auth.domain)
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.presentation)

            // Bare KMP lifecycle (ViewModel + viewModelScope) and Koin VM DSL — NOT the
            // -compose variants, which pull canvas compose.ui → skiko onto the Kobweb classpath.
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlin.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.bundles.client.test)
        }
    }
}
