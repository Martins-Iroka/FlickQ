rootProject.name = "FlickQ"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":app:androidApp")
include(":app:shared")
include(":app:webApp")
include(":server")
include(":core:domain")
include(":core:api")
include(":core:common")
include(":core:data")
include(":core:presentation")
include(":core:design-system")
include(":feature:auth:domain")
include(":feature:auth:data")
include(":feature:auth:presentation")
include(":feature:movie:domain")
include(":feature:movie:data")
include(":feature:movie:presentation")
include(":feature:showtime:domain")
include(":feature:showtime:data")
include(":feature:showtime:presentation")
include(":feature:booking:domain")
include(":feature:booking:data")
include(":feature:booking:presentation")
include(":feature:payment:domain")
include(":feature:payment:data")
include(":feature:payment:presentation")
include(":feature:admin:domain")
include(":feature:admin:data")
include(":feature:admin:presentation")
include(":app:adminWeb")
include(":app:adminKobweb")
