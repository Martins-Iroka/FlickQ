import com.martdev.flickq.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class FeatureDataLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("flickq.kmp.feature.library")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets {
                    commonMain.dependencies {
                        implementation(project(":core:api"))
                        implementation(
                            libs.findLibrary("ktor-client-core").get()
                        )
                        implementation(libs.findLibrary("koin-core").get())
                        implementation(
                            libs.findLibrary("kotlin-datetime").get()
                        )
                    }
                    commonTest.dependencies {
                        implementation(
                            libs.findLibrary("ktor-client-mock").get()
                        )
                        implementation(
                            libs.findLibrary("ktor-client-contentNegotiation").get()
                        )
                        implementation(
                            libs.findLibrary("ktor-serialization-kotlinx-json").get()
                        )
                    }
                }
            }
        }
    }
}