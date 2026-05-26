import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.flyway)
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

group = "com.martdev.flickq"
version = "1.0.0"
application {
    mainClass = "com.martdev.flickq.ApplicationKt"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    ksp(libs.koin.annotation.compiler)
    implementation(projects.core.domain)
    implementation(libs.bcrypt)
    implementation(libs.bundles.exposed.libs)
    implementation(libs.bundles.ktor.libs)
    implementation(libs.dotenv)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.hikariCP)
    implementation(libs.bundles.koin.libs)
    implementation(libs.postgresql)
    implementation(libs.stytch)
    implementation(libs.testcontainers.postgresql)
    testImplementation(libs.bundles.server.test.libs)
}

tasks.test {
    useJUnitPlatform()
}