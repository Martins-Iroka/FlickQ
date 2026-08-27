import com.martdev.flickq.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KMPLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.findPlugin("kotlinMultiplatform").get().get().pluginId)
                apply(libs.findPlugin("androidMultiplatformLibrary").get().get().pluginId)
            }

            extensions.configure<KotlinMultiplatformExtension> {
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

                sourceSets {
                    commonMain.dependencies {
                        implementation(project(":core:common"))
                    }
                }
            }
        }
    }
}