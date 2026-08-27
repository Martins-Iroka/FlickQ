
import com.martdev.flickq.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.Actions.with
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KMPFeatureLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("flickq.kmp.library")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets {
                    commonMain.dependencies {
                        implementation(project(":core:domain"))
                        implementation(project(":core:data"))
                    }
                    commonTest.dependencies {
                        implementation(libs.findBundle("client-test").get())
                    }
                }
            }
        }
    }
}