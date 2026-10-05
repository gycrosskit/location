plugins {
    kotlin("multiplatform") version "2.2.21-1.0.0"
    id("com.android.library") version "8.10.1"
}
val locationVersion = providers.gradleProperty("locationVersion").orElse("0.1.3").get()
kotlin {
    androidTarget()
    jvm()
    ohosArm64()
    iosArm64()
    iosX64()
    iosSimulatorArm64 { binaries.framework { baseName = "LocationConsumer" } }
    sourceSets {
        commonMain.dependencies { implementation("com.github.gycrosskit.location:location-core:$locationVersion") }
        ohosArm64Main.dependencies { implementation("com.github.gycrosskit.location:location-kuikly:$locationVersion") }
    }
}
android { namespace = "io.github.gycrosskit.location.consumer"; compileSdk = 36; defaultConfig { minSdk = 24 } }
