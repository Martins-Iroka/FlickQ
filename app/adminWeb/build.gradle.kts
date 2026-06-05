import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).copy(
                    port = 8082, // Change Admin Web to 8082
                )
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // Admin app depends ONLY on core:* + its own admin features + auth data (for the
            // shared /authentication/login binding) — never the customer feature graphs.
            implementation(projects.feature.admin.presentation)
            implementation(projects.feature.admin.data)
            implementation(projects.feature.auth.data)
            implementation(projects.core.data)
            implementation(projects.core.presentation)
            implementation(projects.core.designSystem)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.bundles.client.koin.compose)
        }
    }
}
