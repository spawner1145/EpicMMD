pluginManagement {
    repositories {
        exclusiveContent {
            forRepository {
                maven("https://maven.neoforged.net/releases")
            }
            filter {
                includeGroupAndSubgroups("net.neoforged")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "EpicMMD"
