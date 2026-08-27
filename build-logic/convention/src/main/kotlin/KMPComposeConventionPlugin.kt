import com.martdev.flickq.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.Actions.with
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KMPComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.findPlugin("composeMultiplatform").get().get().pluginId)
                apply(libs.findPlugin("composeCompiler").get().get().pluginId)
            }

            extensions.configure<KotlinMultiplatformExtension> {

                sourceSets {
                    commonMain.dependencies {
                        implementation(libs.findLibrary("compose-runtime").get())
                        implementation(libs.findLibrary("compose-foundation").get())
                        implementation(libs.findLibrary("compose-material3").get())
                        implementation(libs.findLibrary("compose-ui").get())
                        implementation(libs.findLibrary("androidx-lifecycle-viewmodelCompose").get())
                        implementation(libs.findLibrary("androidx-lifecycle-runtimeCompose").get())
                        implementation(libs.findLibrary("navigation-compose").get())
                        implementation(libs.findBundle("client-koin-compose").get())
                    }
                }
            }
        }
    }

}