plugins {
    `kotlin-dsl`
}

group = "com.martdev.flickq.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "flickq.kmp.library"
            implementationClass = "KMPLibraryConventionPlugin"
        }
        register("cmpLibrary") {
            id = "flickq.cmp.library"
            implementationClass = "KMPComposeConventionPlugin"
        }
        register("kmpFeatureLibrary") {
            id = "flickq.kmp.feature.library"
            implementationClass = "KMPFeatureLibraryConventionPlugin"
        }
        register("featureDataLibrary") {
            id = "flickq.feature.data.library"
            implementationClass = "FeatureDataLibraryConventionPlugin"
        }
        register("featurePresentationLibrary") {
            id = "flickq.feature.presentation.library"
            implementationClass = "FeaturePresentationConventionPlugin"
        }
    }
}