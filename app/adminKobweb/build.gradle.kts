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

            // Spike: prove a 2.3.21-compiled shared klib is consumable from the Kobweb (Kotlin/JS) module.
            implementation(projects.core.data)
        }
    }
}
