plugins {
    kotlin("multiplatform") version "2.2.21-1.0.0"
    id("com.android.library") version "8.10.1"
}
val locationVersion = providers.gradleProperty("locationVersion").orElse("0.1.6").get()
val verifyKuiklyNative = locationVersion !in setOf("0.1.0", "0.1.1", "0.1.2", "0.1.3", "0.1.4", "0.1.5")
val kuiklyRenderFrameworkDir = providers.gradleProperty("kuiklyRenderFrameworkDir").orNull
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
    jvm()
    iosArm64()
    iosX64 { binaries.framework {
        baseName = "LocationConsumer"
        export("com.github.gycrosskit.location:location-core:$locationVersion")
        if (verifyKuiklyNative) {
            export("com.github.gycrosskit.location:location-kuikly:$locationVersion")
            kuiklyRenderFrameworkDir?.let { linkerOpts("-F$it", "-framework", "OpenKuiklyIOSRender") }
        }
    } }
    iosSimulatorArm64 { binaries.framework {
        baseName = "LocationConsumer"
        export("com.github.gycrosskit.location:location-core:$locationVersion")
        if (verifyKuiklyNative) {
            export("com.github.gycrosskit.location:location-kuikly:$locationVersion")
            kuiklyRenderFrameworkDir?.let { linkerOpts("-F$it", "-framework", "OpenKuiklyIOSRender") }
        }
    } }
    ohosArm64()
    sourceSets {
        commonMain.dependencies { api("com.github.gycrosskit.location:location-core:$locationVersion") }
        ohosArm64Main.dependencies { implementation("com.github.gycrosskit.location:location-kuikly:$locationVersion") }
        if (verifyKuiklyNative) {
            androidMain.get().kotlin.srcDir("src/kuiklyNativeAndroidMain/kotlin")
            iosMain.get().kotlin.srcDir("src/kuiklyNativeIosMain/kotlin")
            androidMain.dependencies { api("com.github.gycrosskit.location:location-kuikly:$locationVersion") }
            iosMain.dependencies { api("com.github.gycrosskit.location:location-kuikly:$locationVersion") }
        }
    }
}
android {
    namespace = "io.github.gycrosskit.location.consumer"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
