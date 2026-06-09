import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kobwebApplication)
}

group = "com.martdev.flickq.adminkobweb"
version = "1.0-SNAPSHOT"

kobweb {
    app {
        index {
            description.set("FlickQ Admin")
        }
    }
}

kotlin {
    // JS-only Kobweb application (no Kobweb backend server — we hit the existing Ktor API).
    configAsKobwebApplication("adminkobweb")

    sourceSets {
        jsMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.html.core)
            implementation(libs.kobweb.core)
            implementation(libs.kobweb.silk)
            implementation(libs.silk.icons.fa)

            // Reused (UI-agnostic) shared layers — NEVER :feature:admin:presentation /
            // :core:design-system (those pull Compose-Multiplatform canvas UI).
            implementation(projects.core.data)
            implementation(projects.core.presentation)
            implementation(projects.feature.admin.presentationLogic)
            implementation(projects.feature.auth.data)

            // koin-core only — NOT koin-compose-viewmodel (it pulls canvas compose.ui via
            // lifecycle-viewmodel-compose). ViewModels are obtained via KoinPlatform.getKoin().
            implementation(libs.koin.core)
        }
    }
}
