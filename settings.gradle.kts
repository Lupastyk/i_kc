pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
        kotlin("jvm") version "1.9.10"
        kotlin("plugin.allopen") version "1.9.10"
    }
}

rootProject.name = "finance-microservices"

include("token-service")
include("transactions-service")
