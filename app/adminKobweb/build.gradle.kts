import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication
import kotlinx.html.link

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kobwebApplication)
}

group = "com.martdev.flickq.adminkobweb"
version = "1.0-SNAPSHOT"

kobweb {
    app {
        export {
            suppressNoRootWarning.set(true)
        }
        index {
            description.set("FlickQ Admin")
            // CineAdmin type system: Inter (UI) + Montserrat (headings).
            head.add {
                link(rel = "preconnect", href = "https://fonts.googleapis.com")
                link(
                    rel = "stylesheet",
                    href = "https://fonts.googleapis.com/css2?" +
                        "family=Inter:wght@400;500;600;700&" +
                        "family=Montserrat:wght@600;700&display=swap",
                )
            }
        }
    }
}

kotlin {
    // JS-only Kobweb application (no Kobweb backend server — we hit the existing Ktor API).
    configAsKobwebApplication("adminkobweb", includeServer = false)

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
            implementation(projects.core.domain)
            implementation(projects.core.presentation)
            implementation(projects.feature.admin.presentationLogic)
            implementation(projects.feature.admin.data)
            implementation(projects.feature.auth.data)

            // Bare lifecycle (ViewModel + ViewModelStore for the page-scoped VM holder) and
            // koin-core only — NOT the -compose variants (they pull canvas compose.ui via
            // lifecycle-viewmodel-compose). ViewModels are obtained via KoinPlatform.getKoin().
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.koin.core)
            // Movie/Showtime models expose kotlinx-datetime types (LocalDate); core.domain pulls it
            // via `implementation` so it doesn't leak to us — declare it directly for formatting.
            implementation(libs.kotlin.datetime)
            implementation(libs.kotlin.serialization)
        }
    }
}
