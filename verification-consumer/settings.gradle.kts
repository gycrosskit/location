pluginManagement {
    repositories {
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        google(); mavenCentral(); gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven(providers.gradleProperty("locationRepository").orElse("https://jitpack.io").get()) {
            content { includeGroup("com.github.gycrosskit.location") }
        }
        maven("https://maven.eazytec-cloud.com/nexus/repository/maven-public/")
        google(); mavenCentral()
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/") {
            content { includeGroup("com.tencent.kuikly-open") }
        }
    }
}
rootProject.name = "location-consumer"
